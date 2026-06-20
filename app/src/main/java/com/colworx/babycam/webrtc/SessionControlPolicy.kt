package com.colworx.babycam.webrtc

data class SessionControlState(
    val camera: Boolean = false,
    val microphone: Boolean = false,
    val torch: Boolean = false,
    val cryDetection: Boolean = false,
    val lullaby: Boolean = false,
    val parentCamera: Boolean = false,
    val parentTalk: Boolean = false,
    val videoSaver: Boolean = false,
) {
    fun failSafeOff(): SessionControlState = copy(
        camera = false,
        microphone = false,
        torch = false,
        cryDetection = false,
        lullaby = false,
        parentCamera = false,
        parentTalk = false,
    )

    fun commands(): List<Pair<String, Boolean>> = listOf(
        CONTROL_CAMERA to camera,
        CONTROL_MICROPHONE to microphone,
        CONTROL_TORCH to torch,
        CONTROL_CRY to cryDetection,
        CONTROL_LULLABY to lullaby,
        CONTROL_PARENT_CAMERA to parentCamera,
        CONTROL_PARENT_TALK to parentTalk,
        CONTROL_VIDEO_SAVER to videoSaver,
    )

    fun withControl(name: String, enabled: Boolean): SessionControlState = when (name) {
        CONTROL_CAMERA -> copy(camera = enabled)
        CONTROL_MICROPHONE -> copy(microphone = enabled)
        CONTROL_TORCH -> copy(torch = enabled)
        CONTROL_CRY -> copy(cryDetection = enabled)
        CONTROL_LULLABY -> copy(lullaby = enabled)
        CONTROL_PARENT_CAMERA -> copy(parentCamera = enabled)
        CONTROL_PARENT_TALK -> copy(parentTalk = enabled)
        CONTROL_VIDEO_SAVER -> copy(videoSaver = enabled)
        else -> this
    }
}

class SessionSyncGate {
    private var acceptedId: String? = null

    @Synchronized
    fun accept(syncId: String): Boolean {
        if (syncId.isBlank() || acceptedId == syncId) return false
        acceptedId = syncId
        return true
    }

    @Synchronized
    fun currentId(): String? = acceptedId
}

class ControlRevisionGate {
    private val latestByControl = mutableMapOf<String, Long>()

    @Synchronized
    fun accept(control: String, revision: Long): Boolean {
        if (revision <= 0L || revision <= latestByControl.getOrDefault(control, 0L)) return false
        latestByControl[control] = revision
        return true
    }

    @Synchronized
    fun reset() {
        latestByControl.clear()
    }
}

class ParentCommandTracker {
    private var nextRevision = 0L
    private val pendingByControl = mutableMapOf<String, Long>()

    @Synchronized
    fun next(control: String): Long {
        val revision = ++nextRevision
        pendingByControl[control] = revision
        return revision
    }

    @Synchronized
    fun acceptAck(control: String, revision: Long): Boolean {
        if (pendingByControl[control] != revision) return false
        pendingByControl.remove(control)
        return true
    }
}

class ParentSyncTracker {
    private var activeSyncId: String? = null
    private var acknowledged = false

    @Synchronized
    fun begin(syncId: String) {
        activeSyncId = syncId
        acknowledged = false
    }

    @Synchronized
    fun acknowledge(syncId: String): Boolean {
        if (syncId.isBlank() || syncId != activeSyncId || acknowledged) return false
        acknowledged = true
        return true
    }

    @Synchronized
    fun shouldRetry(syncId: String): Boolean = activeSyncId == syncId && !acknowledged
}

fun cameraActualState(requestedOn: Boolean, transitionSucceeded: Boolean): Boolean =
    requestedOn && transitionSucceeded

const val CONTROL_CAMERA = "camera"
const val CONTROL_MICROPHONE = "microphone"
const val CONTROL_TORCH = "torch"
const val CONTROL_CRY = "cry"
const val CONTROL_LULLABY = "lullaby"
const val CONTROL_PARENT_CAMERA = "parent_camera"
const val CONTROL_PARENT_TALK = "parent_talk"
const val CONTROL_VIDEO_SAVER = "video_saver"
