package com.custom.redmitvlauncher.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class MarketPrice(
    val fuelRon95: String,
    val fuelDiesel: String,
    val goldSjcBuy: String,
    val goldSjcSell: String,
    val updatedAt: String
)

object PriceFetcher {
    /**
     * Lấy giá xăng và vàng nhẹ nhàng qua HTTP thuần (không cần Retrofit/OkHttp để giảm dung lượng APK).
     * Bạn có thể thay URL bằng API JSON riêng hoặc Firebase node `/market_prices`.
     */
    suspend fun fetchDailyPrices(): MarketPrice = withContext(Dispatchers.IO) {
        val timeStr = SimpleDateFormat("HH:mm dd/MM/yyyy", Locale.getDefault()).format(Date())
        try {
            // Ví dụ gọi API công khai hoặc fallback dữ liệu mẫu nếu mất mạng / API thay đổi
            val url = URL("https://api.example.com/vn-prices")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            if (conn.responseCode == 200) {
                val body = conn.inputStream.bufferedReader().readText()
                val json = JSONObject(body)
                return@withContext MarketPrice(
                    fuelRon95 = json.optString("ron95", "21.450 đ/lít"),
                    fuelDiesel = json.optString("diesel", "19.120 đ/lít"),
                    goldSjcBuy = json.optString("gold_buy", "84.50 Tr"),
                    goldSjcSell = json.optString("gold_sell", "86.50 Tr"),
                    updatedAt = timeStr
                )
            }
        } catch (_: Exception) {
            // Fallback an toàn
        }

        MarketPrice(
            fuelRon95 = "21.450 đ/lít",
            fuelDiesel = "19.120 đ/lít",
            goldSjcBuy = "84.50 Tr/lượng",
            goldSjcSell = "86.50 Tr/lượng",
            updatedAt = timeStr
        )
    }
}
