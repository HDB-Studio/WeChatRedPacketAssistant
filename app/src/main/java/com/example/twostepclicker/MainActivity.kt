package com.example.twostepclicker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private companion object {
        const val CHANNEL_ID = "run_channel"
        const val NOTIF_ID = 1001
        const val REQ_NOTIF = 10
    }

    private lateinit var statusText: TextView
    private lateinit var etTarget: EditText

    private lateinit var rg1: RadioGroup
    private lateinit var etValue1: EditText
    private lateinit var etX1: EditText
    private lateinit var etY1: EditText
    private lateinit var etWait1: EditText

    private lateinit var rg2: RadioGroup
    private lateinit var etValue2: EditText
    private lateinit var etX2: EditText
    private lateinit var etY2: EditText
    private lateinit var etWait2: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        etTarget = findViewById(R.id.etTarget)

        rg1 = findViewById(R.id.rg1)
        etValue1 = findViewById(R.id.etValue1)
        etX1 = findViewById(R.id.etX1)
        etY1 = findViewById(R.id.etY1)
        etWait1 = findViewById(R.id.etWait1)

        rg2 = findViewById(R.id.rg2)
        etValue2 = findViewById(R.id.etValue2)
        etX2 = findViewById(R.id.etX2)
        etY2 = findViewById(R.id.etY2)
        etWait2 = findViewById(R.id.etWait2)

        findViewById<Button>(R.id.btnOpenSettings).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        findViewById<Button>(R.id.btnSave).setOnClickListener {
            saveConfig()
            toast("設定已儲存")
        }

        findViewById<Button>(R.id.btnNotify).setOnClickListener {
            saveConfig()
            requestNotifPermissionIfNeeded()
            showRunNotification()
            toast("已顯示通知，請切到目標 App 下拉通知欄點「執行」")
        }

        findViewById<Button>(R.id.btnRun).setOnClickListener {
            saveConfig()
            triggerRun()
        }

        loadConfig()
        requestNotifPermissionIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun isAccessibilityEnabled(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.split(':').any { it.startsWith("$packageName/") }
    }

    private fun updateStatus() {
        if (isAccessibilityEnabled()) {
            statusText.text = "無障礙服務：已開啟"
            statusText.setTextColor(0xFF2E7D32.toInt())
        } else {
            statusText.text = "無障礙服務：未開啟（請先到設定開啟）"
            statusText.setTextColor(0xFFD32F2F.toInt())
        }
    }

    private fun stepKindOf(checkedId: Int, textId: Int, descId: Int, idId: Int): StepKind =
        when (checkedId) {
            textId -> StepKind.TEXT
            descId -> StepKind.DESC
            idId -> StepKind.ID
            else -> StepKind.POINT
        }

    private fun collectSteps(): List<Step> = listOf(
        Step(
            kind = stepKindOf(rg1.checkedRadioButtonId, R.id.rb1Text, R.id.rb1Desc, R.id.rb1Id),
            value = etValue1.text.toString().trim(),
            xPercent = etX1.text.toString().toIntOrNull() ?: 50,
            yPercent = etY1.text.toString().toIntOrNull() ?: 50,
            waitBeforeMs = etWait1.text.toString().toLongOrNull() ?: 500L
        ),
        Step(
            kind = stepKindOf(rg2.checkedRadioButtonId, R.id.rb2Text, R.id.rb2Desc, R.id.rb2Id),
            value = etValue2.text.toString().trim(),
            xPercent = etX2.text.toString().toIntOrNull() ?: 50,
            yPercent = etY2.text.toString().toIntOrNull() ?: 50,
            waitBeforeMs = etWait2.text.toString().toLongOrNull() ?: 800L
        )
    )

    private fun saveConfig() {
        StepStore.save(this, collectSteps())
        StepStore.setTargetPackage(this, etTarget.text.toString())
    }

    private fun loadConfig() {
        val steps = StepStore.load(this)
        val s1 = steps.getOrNull(0)
        val s2 = steps.getOrNull(1)
        val defaults = listOf(
            Step(waitBeforeMs = 500L),
            Step(waitBeforeMs = 800L)
        )
        bindStep(
            s1 ?: defaults[0],
            rg1, R.id.rb1Text, R.id.rb1Desc, R.id.rb1Id, R.id.rb1Point,
            etValue1, etX1, etY1, etWait1
        )
        bindStep(
            s2 ?: defaults[1],
            rg2, R.id.rb2Text, R.id.rb2Desc, R.id.rb2Id, R.id.rb2Point,
            etValue2, etX2, etY2, etWait2
        )
        etTarget.setText(StepStore.targetPackage(this))
    }

    private fun bindStep(
        step: Step,
        group: RadioGroup,
        textId: Int,
        descId: Int,
        viewIdId: Int,
        pointId: Int,
        value: EditText,
        x: EditText,
        y: EditText,
        wait: EditText
    ) {
        group.check(
            when (step.kind) {
                StepKind.TEXT -> textId
                StepKind.DESC -> descId
                StepKind.ID -> viewIdId
                StepKind.POINT -> pointId
            }
        )
        value.setText(step.value)
        x.setText(step.xPercent.toString())
        y.setText(step.yPercent.toString())
        wait.setText(step.waitBeforeMs.toString())
    }

    private fun triggerRun() {
        if (!isAccessibilityEnabled()) {
            toast("請先開啟無障礙服務")
            return
        }
        if (TwoStepAccessibilityService.instance == null) {
            toast("服務尚未連線，請稍候或重新開啟無障礙服務")
            return
        }
        sendBroadcast(
            Intent(TwoStepAccessibilityService.ACTION_RUN).setPackage(packageName)
        )
    }

    private fun requestNotifPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIF)
            }
        }
    }

    private fun showRunNotification() {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "執行觸發",
                    NotificationManager.IMPORTANCE_HIGH
                )
            )
        }

        val runIntent = PendingIntent.getBroadcast(
            this,
            1,
            Intent(TwoStepAccessibilityService.ACTION_RUN).setPackage(packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("雙步點擊已就緒")
            .setContentText("切到目標 App 後點下方「執行」")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(0, "執行兩步點擊", runIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            toast("請允許通知權限，否則看不到執行按鈕")
            return
        }
        NotificationManagerCompat.from(this).notify(NOTIF_ID, notification)
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
