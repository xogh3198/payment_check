package kr.co.bitecompany.depositagent

import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var preferences: AgentPreferences
    private lateinit var eventStore: EventStore
    private lateinit var webhookInput: EditText
    private lateinit var secretInput: EditText
    private lateinit var listenerStatus: TextView
    private lateinit var eventsContainer: LinearLayout

    private val eventsChangedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refreshEvents()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferences = AgentPreferences(applicationContext)
        eventStore = EventStore(applicationContext)
        setContentView(createContent())
        registerEventsReceiver()
    }

    override fun onResume() {
        super.onResume()
        refreshListenerStatus()
        refreshEvents()
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(eventsChangedReceiver) }
        super.onDestroy()
    }

    private fun createContent(): View {
        val scrollView = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#F4F6F5"))
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(40))
        }
        scrollView.addView(content)

        content.addView(text("MarketBite 입금 확인", 24f, Typeface.BOLD, "#17211F"))
        content.addView(text("은행 입금 알림 웹훅 전송 앱", 13f, Typeface.NORMAL, "#52605D").withBottom(18))

        content.addView(panel().apply {
            addView(text("검증용 앱", 14f, Typeface.BOLD, "#7B4C00"))
            addView(text(
                "이 앱은 계좌 원장을 조회하지 않습니다. 국민·하나·농협은행 입금 알림을 감지해 웹훅으로 전달합니다.",
                14f,
                Typeface.NORMAL,
                "#5F4B20",
            ).withTop(6))
        }.withBottom(14))

        listenerStatus = text("", 15f, Typeface.BOLD, "#17211F")
        content.addView(panel().apply {
            addView(text("알림 접근", 12f, Typeface.BOLD, "#66716F"))
            addView(listenerStatus.withTop(6))
            addView(button("알림 접근 설정 열기") {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }.withTop(10))
        }.withBottom(14))

        content.addView(panel().apply {
            addView(text("Webhook 설정", 18f, Typeface.BOLD, "#17211F"))
            addView(text(
                "에뮬레이터는 http://10.0.2.2:8787/webhook/deposits 를 사용합니다.",
                12f,
                Typeface.NORMAL,
                "#66716F",
            ).withTop(4))

            webhookInput = EditText(this@MainActivity).apply {
                hint = "http://노트북-IP:8787/webhook/deposits"
                setText(preferences.webhookUrl)
                setSingleLine(true)
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            }
            addView(webhookInput.withTop(10))

            secretInput = EditText(this@MainActivity).apply {
                hint = "공유 시크릿 (선택)"
                setText(preferences.webhookSecret)
                setSingleLine(true)
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            addView(secretInput.withTop(4))

            addView(button("설정 저장") { saveSettings() }.withTop(10))
            addView(text("Device ID: ${preferences.deviceId}", 11f, Typeface.NORMAL, "#7B8583").withTop(8))
        }.withBottom(14))

        content.addView(panel().apply {
            addView(text("단계별 확인", 18f, Typeface.BOLD, "#17211F"))
            addView(button("국민은행 샘플 웹훅 전송") { sendSample("KB") }.withTop(10))
            addView(button("하나은행 샘플 웹훅 전송") { sendSample("HANA") }.withTop(6))
            addView(button("농협은행 샘플 웹훅 전송") { sendSample("NH") }.withTop(6))
            addView(button("실패·대기 이벤트 다시 전송") { retryPendingEvents() }.withTop(6))
            addView(button("최근 내역 새로고침") { refreshEvents() }.withTop(6))
            addView(button("단말 내역 지우기") { confirmClear() }.withTop(6))
        }.withBottom(14))

        content.addView(text("최근 감지 내역", 18f, Typeface.BOLD, "#17211F").withBottom(8))
        eventsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        content.addView(eventsContainer)
        return scrollView
    }

    private fun saveSettings() {
        val url = webhookInput.text.toString().trim()
        if (url.isNotBlank() && !url.startsWith("http://") && !url.startsWith("https://")) {
            Toast.makeText(this, "Webhook URL은 http:// 또는 https://로 시작해야 합니다.", Toast.LENGTH_LONG).show()
            return
        }
        preferences.webhookUrl = url
        preferences.webhookSecret = secretInput.text.toString()
        Toast.makeText(this, "설정을 저장했습니다.", Toast.LENGTH_SHORT).show()
    }

    private fun sendSample(bank: String) {
        saveSettings()
        val now = System.currentTimeMillis()
        val title: String
        val sample: String
        when (bank) {
            "HANA" -> {
                title = "하나원큐 입출금 알림"
                sample = """
                    [하나은행] 08/18 14:31
                    123-9100-****
                    입금 30,000원
                    이태호4821
                    잔액 120,000원
                """.trimIndent()
            }
            "NH" -> {
                title = "NH스마트뱅킹 입출금 알림"
                sample = """
                    [NH농협] 08/18 14:32
                    302-****-1234-**
                    30,000원 입금
                    이태호4821
                    잔액 120,000원
                """.trimIndent()
            }
            else -> {
                title = "KB 입금 알림 샘플"
                sample = """
                    [KB]8/18 14:30
                    498125****8895
                    이태호4821
                    30,000 입금
                    1644-9999
                """.trimIndent()
            }
        }
        val event = DepositNotificationParser.parse(
            packageName = "poc.sample",
            title = title,
            body = sample,
            postedAt = now,
            deviceId = preferences.deviceId,
        ) ?: return
        eventStore.add(event)
        refreshEvents()
        if (preferences.webhookUrl.isBlank()) {
            Toast.makeText(this, "이벤트를 저장했습니다. Webhook URL을 입력하면 전송할 수 있습니다.", Toast.LENGTH_LONG).show()
        } else {
            WebhookSender.send(applicationContext, event)
        }
    }

    private fun retryPendingEvents() {
        saveSettings()
        if (preferences.webhookUrl.isBlank()) {
            Toast.makeText(this, "Webhook URL을 먼저 입력해주세요.", Toast.LENGTH_LONG).show()
            return
        }
        val retryEvents = eventStore.all().filter { it.deliveryStatus != "DELIVERED" }
        retryEvents.forEach { WebhookSender.send(applicationContext, it) }
        Toast.makeText(this, "${retryEvents.size}건을 다시 전송합니다.", Toast.LENGTH_SHORT).show()
    }

    private fun confirmClear() {
        AlertDialog.Builder(this)
            .setTitle("최근 내역 지우기")
            .setMessage("단말에 저장된 PoC 이벤트만 삭제합니다.")
            .setNegativeButton("취소", null)
            .setPositiveButton("삭제") { _, _ ->
                eventStore.clear()
                refreshEvents()
            }
            .show()
    }

    private fun refreshListenerStatus() {
        val enabledListeners = Settings.Secure.getString(
            contentResolver,
            "enabled_notification_listeners",
        ).orEmpty()
        val enabled = enabledListeners.contains(packageName)
        listenerStatus.text = if (enabled) "사용 중" else "권한 필요"
        listenerStatus.setTextColor(Color.parseColor(if (enabled) "#176B5B" else "#A23B2A"))
    }

    private fun refreshEvents() {
        if (!::eventsContainer.isInitialized) return
        eventsContainer.removeAllViews()
        val events = eventStore.all()
        if (events.isEmpty()) {
            eventsContainer.addView(text("아직 감지된 입금 알림이 없습니다.", 14f, Typeface.NORMAL, "#66716F"))
            return
        }

        events.forEach { event ->
            val amount = event.amount?.let {
                NumberFormat.getNumberInstance(Locale.KOREA).format(it) + "원"
            } ?: "금액 확인 필요"
            val deliveryLabel = when (event.deliveryStatus) {
                "DELIVERED" -> "전송 완료"
                "FAILED" -> "전송 실패"
                else -> "전송 대기"
            }
            val statusColor = when (event.deliveryStatus) {
                "DELIVERED" -> "#176B5B"
                "FAILED" -> "#A23B2A"
                else -> "#7B5B00"
            }
            val time = runCatching {
                Instant.parse(event.receivedAt)
                    .atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("MM/dd HH:mm:ss"))
            }.getOrDefault(event.receivedAt)

            eventsContainer.addView(panel().apply {
                addView(text("${bankLabel(event.bank)} · $deliveryLabel", 12f, Typeface.BOLD, statusColor))
                addView(text("$amount · ${event.depositorName ?: "입금자 확인 필요"}", 18f, Typeface.BOLD, "#17211F").withTop(5))
                addView(text("$time · ${event.accountMasked ?: "계좌 확인 필요"}", 12f, Typeface.NORMAL, "#66716F").withTop(4))
                addView(text("파싱: ${event.parseStatus} · 시도: ${event.deliveryAttempts}", 11f, Typeface.NORMAL, "#7B8583").withTop(3))
                if (!event.lastError.isNullOrBlank()) {
                    addView(text(event.lastError, 11f, Typeface.NORMAL, "#A23B2A").withTop(4))
                }
                addView(text(event.rawText, 12f, Typeface.NORMAL, "#3C4946").withTop(8))
            }.withBottom(8))
        }
    }

    private fun bankLabel(bank: String): String = when (bank) {
        "KB" -> "국민은행"
        "HANA" -> "하나은행"
        "NH" -> "농협은행"
        else -> bank
    }

    private fun registerEventsReceiver() {
        val filter = IntentFilter(WebhookSender.ACTION_EVENTS_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(eventsChangedReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(eventsChangedReceiver, filter)
        }
    }

    private fun panel(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(16), dp(16), dp(16))
        background = GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = dp(8).toFloat()
            setStroke(dp(1), Color.parseColor("#DCE2E0"))
        }
    }

    private fun button(label: String, action: () -> Unit): Button = Button(this).apply {
        text = label
        isAllCaps = false
        setOnClickListener { action() }
    }

    private fun text(value: String, size: Float, style: Int, color: String): TextView = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(Color.parseColor(color))
        setTypeface(typeface, style)
        setLineSpacing(0f, 1.15f)
    }

    private fun <T : View> T.withTop(value: Int): T = apply {
        layoutParams = marginLayoutParams().also { it.topMargin = dp(value) }
    }

    private fun <T : View> T.withBottom(value: Int): T = apply {
        layoutParams = marginLayoutParams().also { it.bottomMargin = dp(value) }
    }

    private fun View.marginLayoutParams(): LinearLayout.LayoutParams {
        return (layoutParams as? LinearLayout.LayoutParams)
            ?: LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
