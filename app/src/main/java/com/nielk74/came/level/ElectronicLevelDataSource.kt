package com.nielk74.came.level

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.view.Display
import android.view.Surface
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ElectronicLevelStatus {
    DISABLED,
    PAUSED,
    WAITING_FOR_READING,
    ACTIVE,
    SENSOR_UNAVAILABLE,
}

data class ElectronicLevelState(
    val status: ElectronicLevelStatus = ElectronicLevelStatus.DISABLED,
    val rollDegrees: Float = 0f,
) {
    val isActive: Boolean
        get() = status == ElectronicLevelStatus.ACTIVE

    val isLevel: Boolean
        get() = isActive && ElectronicLevelMath.isNearLevel(rollDegrees)
}

/**
 * Lifecycle-aware source for a camera electronic level.
 *
 * [Sensor.TYPE_GAME_ROTATION_VECTOR] is Android's gyro/accelerometer-fused orientation sensor and
 * is ideal here because a horizon does not need magnetic north. The regular rotation vector is a
 * fallback for devices that do not expose the game variant. No sensor is registered until
 * [enabled] is true and the lifecycle is started.
 */
class ElectronicLevelDataSource(
    context: Context,
    private val lifecycle: Lifecycle,
    initiallyEnabled: Boolean = false,
    private val displayRotationProvider: () -> Int = {
        context.getSystemService(DisplayManager::class.java)
            ?.getDisplay(Display.DEFAULT_DISPLAY)
            ?.rotation
            ?: Surface.ROTATION_0
    },
) : DefaultLifecycleObserver, SensorEventListener, AutoCloseable {
    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val orientationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
        ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val rotationMatrix = FloatArray(9)
    private val smoother = CircularAngleSmoother()
    private val mutableState = MutableStateFlow(ElectronicLevelState())

    val state: StateFlow<ElectronicLevelState> = mutableState.asStateFlow()

    private var lifecycleStarted = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
    private var registered = false
    private var closed = false

    var enabled: Boolean = initiallyEnabled
        set(value) {
            if (field == value || closed) return
            field = value
            updateRegistration()
        }

    init {
        lifecycle.addObserver(this)
        updateRegistration()
    }

    override fun onStart(owner: LifecycleOwner) {
        lifecycleStarted = true
        updateRegistration()
    }

    override fun onStop(owner: LifecycleOwner) {
        lifecycleStarted = false
        unregister()
        if (enabled) {
            mutableState.value = ElectronicLevelState(ElectronicLevelStatus.PAUSED)
        }
    }

    override fun onDestroy(owner: LifecycleOwner) {
        close()
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!registered || event.sensor.type != orientationSensor?.type) return

        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
        // Rotation matrices transform device coordinates into world coordinates. Transposing the
        // world's downward Z axis gives gravity in device coordinates.
        val gravityX = -rotationMatrix[6]
        val gravityY = -rotationMatrix[7]
        val rawRoll = ElectronicLevelMath.rollDegreesFromGravity(
            gravityX = gravityX,
            gravityY = gravityY,
            displayQuarterTurns = displayRotationProvider().toQuarterTurns(),
        )
        if (rawRoll == null) {
            mutableState.value = ElectronicLevelState(ElectronicLevelStatus.WAITING_FOR_READING)
            return
        }

        mutableState.value = ElectronicLevelState(
            status = ElectronicLevelStatus.ACTIVE,
            rollDegrees = smoother.update(rawRoll),
        )
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun close() {
        if (closed) return
        closed = true
        unregister()
        lifecycle.removeObserver(this)
        mutableState.value = ElectronicLevelState(ElectronicLevelStatus.DISABLED)
    }

    private fun updateRegistration() {
        if (!enabled) {
            unregister()
            mutableState.value = ElectronicLevelState(ElectronicLevelStatus.DISABLED)
            return
        }
        if (!lifecycleStarted) {
            unregister()
            mutableState.value = ElectronicLevelState(ElectronicLevelStatus.PAUSED)
            return
        }
        if (orientationSensor == null || sensorManager == null) {
            mutableState.value = ElectronicLevelState(ElectronicLevelStatus.SENSOR_UNAVAILABLE)
            return
        }
        if (registered) return

        smoother.reset()
        registered = sensorManager.registerListener(
            this,
            orientationSensor,
            SensorManager.SENSOR_DELAY_GAME,
        )
        mutableState.value = ElectronicLevelState(
            if (registered) {
                ElectronicLevelStatus.WAITING_FOR_READING
            } else {
                ElectronicLevelStatus.SENSOR_UNAVAILABLE
            },
        )
    }

    private fun unregister() {
        if (registered) sensorManager?.unregisterListener(this)
        registered = false
        smoother.reset()
    }
}

private fun Int.toQuarterTurns(): Int = when (this) {
    Surface.ROTATION_90 -> 1
    Surface.ROTATION_180 -> 2
    Surface.ROTATION_270 -> 3
    else -> 0
}
