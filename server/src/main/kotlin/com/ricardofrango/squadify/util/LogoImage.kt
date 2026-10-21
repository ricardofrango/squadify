package com.ricardofrango.squadify.util

import com.ricardofrango.squadify.exception.ValidationException
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Turns an uploaded logo (PNG or JPEG) into the stored one: fitted inside [LOGO_SIZE]×[LOGO_SIZE] (never
 * enlarged), centred on a transparent square, saved as PNG so transparent crests stay transparent.
 */
object LogoImage {
    const val LOGO_SIZE = 512
    const val MAX_UPLOAD_BYTES = 5 * 1024 * 1024
    // Checked from the header before decoding, so a tiny file claiming a huge picture can't eat the memory.
    private const val MAX_SOURCE_SIDE = 6000
    private val ACCEPTED_FORMATS = setOf("png", "jpeg")

    init {
        System.setProperty("java.awt.headless", "true")
    }

    fun toPng(bytes: ByteArray): ByteArray {
        if (bytes.isEmpty()) throw ValidationException("The image is empty")
        if (bytes.size > MAX_UPLOAD_BYTES) throw ValidationException("The image is larger than 5 MB")
        val source = decode(bytes)
        val scale = minOf(1.0, LOGO_SIZE.toDouble() / max(source.width, source.height))
        val width = max(1, (source.width * scale).roundToInt())
        val height = max(1, (source.height * scale).roundToInt())
        val side = max(width, height)

        val square = BufferedImage(side, side, BufferedImage.TYPE_INT_ARGB)
        val g = square.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.drawImage(downscale(source, width, height), (side - width) / 2, (side - height) / 2, null)
        } finally {
            g.dispose()
        }
        return ByteArrayOutputStream().use { out ->
            ImageIO.write(square, "png", out)
            out.toByteArray()
        }
    }

    private fun decode(bytes: ByteArray): BufferedImage {
        val input = ImageIO.createImageInputStream(ByteArrayInputStream(bytes))
            ?: throw ValidationException("Couldn't read the image")
        input.use { stream ->
            val reader = ImageIO.getImageReaders(stream).asSequence().firstOrNull()
                ?: throw ValidationException("Use a PNG or JPEG image")
            try {
                if (reader.formatName.lowercase() !in ACCEPTED_FORMATS) throw ValidationException("Use a PNG or JPEG image")
                reader.input = stream
                if (reader.getWidth(0) > MAX_SOURCE_SIDE || reader.getHeight(0) > MAX_SOURCE_SIDE) {
                    throw ValidationException("The image is too large (at most ${MAX_SOURCE_SIDE}×$MAX_SOURCE_SIDE pixels)")
                }
                return try {
                    reader.read(0)
                } catch (e: Exception) {
                    throw ValidationException("Couldn't read the image")
                }
            } finally {
                reader.dispose()
            }
        }
    }

    // Halving step by step keeps a big photo smooth; one bicubic jump from 4000px to 512px looks jagged.
    private fun downscale(image: BufferedImage, width: Int, height: Int): BufferedImage {
        var current = toArgb(image)
        while (current.width / 2 >= width && current.height / 2 >= height) {
            current = resize(current, current.width / 2, current.height / 2)
        }
        return if (current.width == width && current.height == height) current else resize(current, width, height)
    }

    private fun toArgb(image: BufferedImage): BufferedImage =
        if (image.type == BufferedImage.TYPE_INT_ARGB) image else resize(image, image.width, image.height)

    private fun resize(image: BufferedImage, width: Int, height: Int): BufferedImage {
        val out = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g = out.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            g.drawImage(image, 0, 0, width, height, null)
        } finally {
            g.dispose()
        }
        return out
    }
}
