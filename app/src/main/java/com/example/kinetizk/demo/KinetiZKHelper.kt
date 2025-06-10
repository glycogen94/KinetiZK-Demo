package com.example.kinetizk.demo

import android.content.Context
import mobile.Mobile
import mobile.SDK
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigInteger

data class ProofResult(
    val proof: String,
    val classification: Int,
    val success: Boolean,
    val score: Double
)

object KinetiZKHelper {

    private const val ASSET = "kinetizk_keys.json"
    @Volatile private var sdk: SDK? = null

    /* ---------- 초기화 ---------- */
    @Synchronized
    fun initialize(ctx: Context) {
        if (sdk != null) return

        val obj = JSONObject(ctx.assets.open(ASSET).bufferedReader().readText())
        val pk = obj.getString("proving_key_base64")
        val vk = obj.getString("verifying_key_base64")
        sdk = Mobile.newSDK(pk, vk)
    }

    /* ---------- 센서 스트림 ---------- */
    fun startCollection() = sdk().startCollection()

    fun addSensorReading(
        ts: Long,
        ax: Double, ay: Double, az: Double,
        gx: Double, gy: Double, gz: Double
    ) = sdk().addSensorReading(          // static 메서드!
            ts, ax, ay, az, gx, gy, gz)

    /* ---------- 수집종료→증명·검증 ---------- */
    fun collectAndProve(): ProofResult = kotlin.runCatching {
        val sdk = sdk()

        /* 1) stopCollection(): byte[] */
        val upMs = System.currentTimeMillis()
        val sensorJson = sdk.stopCollection(upMs)
        /* 2) ★ ExtractFeatures 단계 */
        val featJson = sdk.extractFeatures(sensorJson)
        val score = sdk.getScore(featJson)
        val proofJsonStr = sdk.generateProof(featJson)
        val verified = sdk.verifyProof(proofJsonStr)

        val pj = JSONObject(proofJsonStr)
        val cls = JSONObject(pj.getString("public_inputs_json"))
                     .optInt("class", 0)

        ProofResult(
            proof          = pj.getString("proof_base64"),
            classification = cls,
            success        = verified,
            score          = score
        )
    }.getOrElse { ProofResult("",0,false, 0.0) }

    /* ---------- 내부 ---------- */
    private fun sdk(): SDK =
        sdk ?: error("initialize() 먼저 호출 필요")
}
