package com.checkingcontainer.feature.units

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log

/**
 * Decodifica una imagen de galería/cámara a un Bitmap de software apto para
 * Gemini Nano. Antes se usaba `InputImage.bitmapInternal` (accesor interno de
 * ML Kit que puede devolver null según el origen de la foto) y la IA fallaba
 * en silencio — bug: "no me reconoce nada".
 */
internal fun decodeBitmapForIa(context: Context, uri: Uri, maxSide: Int = 1600): Bitmap? =
    runCatching {
        val bmp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                // Software: Nano/Canvas no aceptan bitmaps de hardware.
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val mayor = maxOf(info.size.width, info.size.height)
                if (mayor > maxSide) {
                    val f = maxSide.toFloat() / mayor
                    decoder.setTargetSize(
                        (info.size.width * f).toInt().coerceAtLeast(1),
                        (info.size.height * f).toInt().coerceAtLeast(1),
                    )
                }
            }
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }
        bmp
    }.onFailure { Log.w("IaBitmap", "No se pudo decodificar la imagen: ${it.message}") }
        .getOrNull()
