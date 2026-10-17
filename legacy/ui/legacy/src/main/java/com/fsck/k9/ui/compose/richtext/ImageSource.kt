package com.fsck.k9.ui.compose.richtext

/**
 * Where the image the user wants to put into the message comes from.
 */
enum class ImageSource {
    /** An image of the gallery or any other file the system's file picker offers. */
    Gallery,

    /** A new photo. */
    Camera,
}

/**
 * Asks to pick an image for the message text; the user chose the image button of the format bar.
 */
fun interface ImagePickListener {
    fun onPickImage(source: ImageSource)
}
