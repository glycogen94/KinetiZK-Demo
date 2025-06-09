package com.example.kinetizk.demo

import android.content.Context
import android.util.Base64
import org.json.JSONObject

/**
 * Loads proving / verifying keys from assets/kinetizk_keys.json
 *
 * Place the generated JSON file under app/src/main/assets.
 */
object KinetiZKKeyLoader {

    private const val ASSET_PATH = "kinetizk_keys.json"

    data class Keys(
        val provingKey: ByteArray,
        val verifyingKey: ByteArray
    )

    /**
     * Reads the JSON, Base64-decodes its fields and returns the raw bytes.
     *
     * @throws Exception if the asset is missing or malformed.
     */
    fun load(context: Context): Keys {
        // 1) assets → String
        val jsonText = context.assets.open(ASSET_PATH)
            .bufferedReader()
            .use { it.readText() }

        // 2) parse
        val json = JSONObject(jsonText)
        val provingB64   = json.getString("proving_key_base64")
        val verifyingB64 = json.getString("verifying_key_base64")

        // 3) Base64 → ByteArray (android.util.Base64 is API-friendly down to 21)
        val provingBytes   = Base64.decode(provingB64,   Base64.DEFAULT)
        val verifyingBytes = Base64.decode(verifyingB64, Base64.DEFAULT)

        return Keys(provingBytes, verifyingBytes)
    }
}
