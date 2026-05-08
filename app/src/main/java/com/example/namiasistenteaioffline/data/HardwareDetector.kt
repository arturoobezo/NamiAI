package com.example.namiasistenteaioffline.data

import android.content.Context
import android.os.Build
import android.util.Log
import com.google.ai.edge.litertlm.Backend

object HardwareDetector {
    
    data class HardwareCapabilities(
        val supportsGpu: Boolean,
        val supportsNpu: Boolean,
        val chipsetInfo: String
    )
    
    fun detectCapabilities(context: Context): HardwareCapabilities {
        val chipset = getChipsetInfo()
        val supportsGpu = testBackend(context, "gpu")
        val supportsNpu = testBackend(context, "npu")
        Log.d("HardwareDetector", "Chipset: $chipset, GPU: $supportsGpu, NPU: $supportsNpu")
        return HardwareCapabilities(supportsGpu, supportsNpu, chipset)
    }

    private fun testBackend(context: Context, type: String): Boolean {
        return try {
            when (type) {
                "gpu" -> {
                    Backend.GPU() // Si no lanza excepción, GPU está disponible
                    true
                }
                "npu" -> {
                    Backend.NPU(nativeLibraryDir = context.applicationInfo.nativeLibraryDir)
                    true
                }
                else -> false
            }
        } catch (e: Exception) {
            Log.d("HardwareDetector", "$type no disponible: ${e.message}")
            false
        }
    }
    
    private fun getChipsetInfo(): String {
        return try {
            val hardware = Build.HARDWARE.lowercase()
            val board = Build.BOARD.lowercase()
            when {
                hardware.contains("qcom") || board.contains("taro") || 
                board.contains("kalama") || board.contains("pineapple") -> "Qualcomm Snapdragon"
                hardware.contains("mt") || board.contains("mt") || 
                board.contains("dimensity") -> "MediaTek Dimensity"
                hardware.contains("exynos") || board.contains("exynos") -> "Samsung Exynos"
                hardware.contains("tensor") || board.contains("slider") -> "Google Tensor"
                else -> "${Build.HARDWARE} / ${Build.BOARD}"
            }
        } catch (e: Exception) { "Desconocido" }
    }
}
