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
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private lateinit var preferences: AgentPreferences
    private lateinit var eventStore: EventStore
    private lateinit var serverInput: EditText
    private lateinit var enrollmentTokenInput: EditText
    private lateinit var listenerStatus: TextView
    private lateinit var batteryStatus: TextView
    private lateinit var enrollmentStatus: TextView
    private lateinit var enrollmentButton: Button
    private lateinit var eventsContainer: LinearLayout
    private val executor = Executors.newSingleThreadExecutor()

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
        if (preferences.isEnrolled) AgentWorkScheduler.scheduleHeartbeat(applicationContext)
    }

    override fun onResume() {
        super.onResume()
        refreshDeviceStatus()
        refreshEnrollmentStatus()
        refreshEvents()
        if (preferences.isEnrolled) AgentWorkScheduler.enqueueHeartbeatNow(applicationContext)
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(eventsChangedReceiver) }
        executor.shutdownNow()
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

        content.addView(text("MarketBite 결제 확인", 24f, Typeface.BOLD, "#17211F"))
        content.addView(text("결제 알림 Agent", 13f, Typeface.NORMAL, "#52605D").withBottom(18))

        content.addView(panel("데이터 수집 안내").apply {
            addView(text(
                "이 앱은 허용된 은행·비플페이 알림에서 결제자명, 금액, 거래시각, 마스킹 계좌와 알림 원문을 추출해 MarketBite 서버로 암호화 전송합니다. 계좌 비밀번호나 계좌 원장은 조회하지 않습니다.",
                13f,
                Typeface.NORMAL,
                "#3C4946",
            ).withTop(6))
        }.withBottom(14))

        listenerStatus = text("", 15f, Typeface.BOLD, "#17211F")
        batteryStatus = text("", 15f, Typeface.BOLD, "#17211F")
        content.addView(panel("단말 상태").apply {
            addView(statusRow("알림 접근", listenerStatus))
            addView(button("알림 접근 설정") {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }.withTop(8))
            addView(statusRow("백그라운드 실행", batteryStatus).withTop(12))
            addView(button("배터리 최적화 설정") {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }.withTop(8))
        }.withBottom(14))

        enrollmentStatus = text("", 15f, Typeface.BOLD, "#17211F")
        content.addView(panel("서버 등록").apply {
            addView(enrollmentStatus)

            serverInput = EditText(this@MainActivity).apply {
                hint = "https://seller-api.marketbite.co.kr"
                setText(preferences.serverBaseUrl)
                setSingleLine(true)
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            }
            addView(serverInput.withTop(10))

            enrollmentTokenInput = EditText(this@MainActivity).apply {
                hint = "Dashboard에서 발급한 등록 토큰"
                setSingleLine(true)
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            addView(enrollmentTokenInput.withTop(4))

            enrollmentButton = button("Agent 등록") { enrollAgent() }
            addView(enrollmentButton.withTop(10))
            addView(button("이 단말의 등록 정보 초기화") { confirmResetEnrollment() }.withTop(6))
            addView(text(
                "설치 ID: ${preferences.installationId}",
                11f,
                Typeface.NORMAL,
                "#7B8583",
            ).withTop(8))
        }.withBottom(14))

        content.addView(panel("연결 확인").apply {
            addView(button("국민은행 샘플 전송") { sendSample("KB") })
            addView(button("하나은행 샘플 전송") { sendSample("HANA") }.withTop(6))
            addView(button("농협은행 샘플 전송") { sendSample("NH") }.withTop(6))
            addView(button("비플페이 수산상품권 샘플 전송") { sendSample("BEEPAY") }.withTop(6))
            addView(button("미전송 내역 다시 전송") { retryPendingEvents() }.withTop(6))
            addView(button("단말 내역 지우기") { confirmClear() }.withTop(6))
        }.withBottom(14))

        content.addView(text("최근 감지 내역", 18f, Typeface.BOLD, "#17211F").withBottom(8))
        eventsContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(eventsContainer)
        return scrollView
    }

    private fun enrollAgent() {
        val baseUrl = AgentPreferences.normalizeBaseUrl(serverInput.text.toString())
        val token = enrollmentTokenInput.text.toString().trim()
        if (!validServerUrl(baseUrl)) {
            toast(if (BuildConfig.DEBUG) "서버 주소를 확인해주세요." else "운영 앱은 HTTPS 서버만 사용할 수 있습니다.")
            return
        }
        if (token.isBlank()) {
            toast("Dashboard에서 발급한 등록 토큰을 입력해주세요.")
            return
        }

        enrollmentButton.isEnabled = false
        enrollmentStatus.text = "등록 중"
        executor.execute {
            runCatching {
                AgentApiClient.enroll(
                    serverBaseUrl = baseUrl,
                    enrollmentToken = token,
                    installationId = preferences.installationId,
                    appVersion = BuildConfig.VERSION_NAME,
                )
            }.onSuccess { enrollment ->
                preferences.saveEnrollment(
                    serverBaseUrl = baseUrl,
                    agentId = enrollment.agentId,
                    agentSecret = enrollment.agentSecret,
                    eventPath = enrollment.eventPath,
                    heartbeatPath = enrollment.heartbeatPath,
                )
                AgentWorkScheduler.scheduleHeartbeat(applicationContext)
                AgentWorkScheduler.enqueuePending(applicationContext)
                runOnUiThread {
                    enrollmentTokenInput.text.clear()
                    enrollmentButton.isEnabled = true
                    refreshEnrollmentStatus()
                    toast("Agent 등록이 완료되었습니다.")
                }
            }.onFailure { error ->
                runOnUiThread {
                    enrollmentButton.isEnabled = true
                    refreshEnrollmentStatus()
                    toast(error.message ?: "Agent 등록에 실패했습니다.")
                }
            }
        }
    }

    private fun sendSample(provider: String) {
        if (!preferences.isEnrolled) {
            toast("Agent를 먼저 등록해주세요.")
            return
        }
        val now = System.currentTimeMillis()
        val (title, sample) = when (provider) {
            "HANA" -> "하나원큐 입출금 알림" to """
                [하나은행] 08/20 14:31
                123-9100-****
                입금 30,000원
                이태호4821
                잔액 120,000원
            """.trimIndent()
            "NH" -> "NH스마트뱅킹 입출금 알림" to """
                [NH농협] 08/20 14:32
                302-****-1234-**
                30,000원 입금
                이태호4821
                잔액 120,000원
            """.trimIndent()
            "BEEPAY" -> "비플페이 결제 완료" to """
                수산대전상품권 결제완료
                결제금액: 30,000원
                결제자: 이태호4821
                08/20 14:33
            """.trimIndent()
            else -> "KB 입금 알림" to """
                [KB]8/20 14:30
                498125****8895
                이태호4821
                30,000 입금
                1644-9999
            """.trimIndent()
        }
        val event = DepositNotificationParser.parse(
            packageName = if (provider == "BEEPAY") "com.bizplay.bizzeropay" else "sample.$provider",
            title = title,
            body = sample,
            postedAt = now,
            deviceId = preferences.installationId,
        ) ?: return toast("샘플 알림을 해석하지 못했습니다.")
        if (eventStore.add(event)) AgentWorkScheduler.enqueueEvent(applicationContext, event.id)
        refreshEvents()
    }

    private fun retryPendingEvents() {
        if (!preferences.isEnrolled) return toast("Agent를 먼저 등록해주세요.")
        val pending = eventStore.pending()
        AgentWorkScheduler.enqueuePending(applicationContext)
        toast("${pending.size}건의 전송을 예약했습니다.")
    }

    private fun confirmResetEnrollment() {
        AlertDialog.Builder(this)
            .setTitle("등록 정보 초기화")
            .setMessage("Dashboard에서 해당 Agent를 폐기한 뒤 초기화해야 합니다. 단말에 저장된 서명 키만 삭제됩니다.")
            .setNegativeButton("취소", null)
            .setPositiveButton("초기화") { _, _ ->
                preferences.clearEnrollment()
                AgentWorkScheduler.cancelHeartbeat(applicationContext)
                refreshEnrollmentStatus()
            }
            .show()
    }

    private fun confirmClear() {
        AlertDialog.Builder(this)
            .setTitle("최근 내역 지우기")
            .setMessage("서버에 전송된 거래 내역은 삭제되지 않습니다.")
            .setNegativeButton("취소", null)
            .setPositiveButton("삭제") { _, _ ->
                eventStore.clear()
                refreshEvents()
            }
            .show()
    }

    private fun refreshDeviceStatus() {
        renderStatus(listenerStatus, AgentDeviceStatus.notificationAccessEnabled(this))
        renderStatus(batteryStatus, AgentDeviceStatus.batteryOptimizationIgnored(this))
    }

    private fun refreshEnrollmentStatus() {
        val enrolled = preferences.isEnrolled
        enrollmentStatus.text = if (enrolled) {
            "등록 완료 · ${preferences.agentId?.takeLast(10)}"
        } else {
            "등록 필요"
        }
        enrollmentStatus.setTextColor(Color.parseColor(if (enrolled) "#176B5B" else "#A23B2A"))
        if (::serverInput.isInitialized) serverInput.isEnabled = !enrolled
        if (::enrollmentTokenInput.isInitialized) enrollmentTokenInput.isEnabled = !enrolled
        if (::enrollmentButton.isInitialized) enrollmentButton.isEnabled = !enrolled
    }

    private fun refreshEvents() {
        if (!::eventsContainer.isInitialized) return
        eventsContainer.removeAllViews()
        val events = eventStore.all()
        if (events.isEmpty()) {
            eventsContainer.addView(text("감지된 결제 알림이 없습니다.", 14f, Typeface.NORMAL, "#66716F"))
            return
        }

        events.forEach { event ->
            val amount = event.amount?.let {
                NumberFormat.getNumberInstance(Locale.KOREA).format(it) + "원"
            } ?: "금액 확인 필요"
            val deliveryLabel = when (event.deliveryStatus) {
                "DELIVERED" -> "전송 완료"
                "RETRYING" -> "재전송 대기"
                "REJECTED" -> "서버 거절"
                "FAILED" -> "전송 실패"
                else -> "전송 대기"
            }
            val statusColor = when (event.deliveryStatus) {
                "DELIVERED" -> "#176B5B"
                "REJECTED", "FAILED" -> "#A23B2A"
                else -> "#7B5B00"
            }
            val time = runCatching {
                Instant.parse(event.receivedAt)
                    .atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("MM/dd HH:mm:ss"))
            }.getOrDefault(event.receivedAt)

            eventsContainer.addView(panel("${providerLabel(event.provider)} · $deliveryLabel").apply {
                addView(text("$amount · ${event.payerName ?: "결제자 확인 필요"}", 18f, Typeface.BOLD, "#17211F"))
                addView(text(
                    "$time · ${paymentMethodLabel(event.paymentMethod)}",
                    12f,
                    Typeface.NORMAL,
                    "#66716F",
                ).withTop(4))
                addView(text(
                    "파싱 ${event.parseStatus} · 전송 ${event.deliveryAttempts}회",
                    11f,
                    Typeface.NORMAL,
                    "#7B8583",
                ).withTop(3))
                if (!event.lastError.isNullOrBlank()) {
                    addView(text(event.lastError, 11f, Typeface.NORMAL, "#A23B2A").withTop(4))
                }
                addView(text(event.rawText, 12f, Typeface.NORMAL, "#3C4946").withTop(8))
                (getChildAt(0) as? TextView)?.setTextColor(Color.parseColor(statusColor))
            }.withBottom(8))
        }
    }

    private fun validServerUrl(value: String): Boolean {
        return if (BuildConfig.DEBUG) {
            value.startsWith("https://") || value.startsWith("http://")
        } else {
            value.startsWith("https://")
        }
    }

    private fun renderStatus(view: TextView, enabled: Boolean) {
        view.text = if (enabled) "사용 중" else "설정 필요"
        view.setTextColor(Color.parseColor(if (enabled) "#176B5B" else "#A23B2A"))
    }

    private fun providerLabel(provider: String): String = when (provider) {
        "KB" -> "국민은행"
        "HANA" -> "하나은행"
        "NH" -> "농협은행"
        "BEEPAY" -> "비플페이"
        else -> provider
    }

    private fun paymentMethodLabel(method: String): String = when (method) {
        "fishery_voucher" -> "수산대전상품권"
        else -> "계좌이체"
    }

    private fun registerEventsReceiver() {
        val filter = IntentFilter(AgentWorkScheduler.ACTION_EVENTS_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(eventsChangedReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(eventsChangedReceiver, filter)
        }
    }

    private fun statusRow(label: String, status: TextView): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        addView(text(label, 13f, Typeface.NORMAL, "#66716F"), LinearLayout.LayoutParams(0, -2, 1f))
        addView(status)
    }

    private fun panel(title: String): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(16), dp(16), dp(16))
        background = GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = dp(8).toFloat()
            setStroke(dp(1), Color.parseColor("#DCE2E0"))
        }
        addView(text(title, 16f, Typeface.BOLD, "#17211F"))
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

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
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
