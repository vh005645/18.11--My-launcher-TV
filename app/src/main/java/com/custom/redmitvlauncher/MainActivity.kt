package com.custom.redmitvlauncher

import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.custom.redmitvlauncher.adapter.AppRowAdapter
import com.custom.redmitvlauncher.databinding.ActivityMainBinding
import com.custom.redmitvlauncher.model.AppItem
import com.custom.redmitvlauncher.util.AppStorageManager
import com.custom.redmitvlauncher.util.PriceFetcher
import com.custom.redmitvlauncher.util.RemoteCommandExecutor
import com.google.firebase.database.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var storageManager: AppStorageManager
    private lateinit var adapter: AppRowAdapter

    private var allApps = mutableListOf<AppItem>()
    private var displayedApps = mutableListOf<AppItem>()

    private var isEditMode = false
    private var showHiddenApps = false
    private var isTvLocked = false

    private lateinit var dbRef: DatabaseReference
    private var lastCommandTimestamp: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        storageManager = AppStorageManager(this)
        val deviceId = storageManager.getDeviceId()
        binding.tvDeviceId.text = "ID: $deviceId"

        setupRecyclerView()
        setupButtons()
        loadInstalledApps()
        loadDailyPrices()
        setupFirebaseListener(deviceId)
        updateClock()
    }

    override fun onResume() {
        super.onResume()
        loadInstalledApps()
    }

    private fun updateClock() {
        val sdf = SimpleDateFormat("HH:mm - EEEE, dd/MM/yyyy", Locale("vi", "VN"))
        binding.tvClock.text = sdf.format(Date())
    }

    private fun setupRecyclerView() {
        adapter = AppRowAdapter(
            items = displayedApps,
            isEditMode = { isEditMode },
            onAppClick = { app ->
                if (isTvLocked) return@AppRowAdapter
                if (isEditMode) {
                    showAppOptionsDialog(app)
                } else {
                    launchApp(app.packageName)
                }
            },
            onAppLongClick = { app ->
                if (!isTvLocked) showAppOptionsDialog(app)
            },
            onMoveLeft = { pos -> moveApp(pos, pos - 1) },
            onMoveRight = { pos -> moveApp(pos, pos + 1) }
        )

        binding.rvApps.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.rvApps.adapter = adapter
    }

    private fun setupButtons() {
        binding.btnToggleEdit.setOnClickListener {
            isEditMode = !isEditMode
            binding.btnToggleEdit.text = if (isEditMode) "Xong sắp xếp" else "Sắp xếp / Ẩn App"
            Toast.makeText(
                this,
                if (isEditMode) "Dùng phím Trái/Phải để đổi chỗ, nhấn OK để Ẩn/Gỡ app" else "Đã lưu vị trí",
                Toast.LENGTH_SHORT
            ).show()
            adapter.notifyDataSetChanged()
        }

        binding.btnToggleHidden.setOnClickListener {
            showHiddenApps = !showHiddenApps
            binding.btnToggleHidden.text = if (showHiddenApps) "Đang hiện App ẩn" else "Xem App đã ẩn"
            refreshDisplayedApps()
        }
    }

    private fun loadInstalledApps() {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val leanbackIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        }

        val resolved = (pm.queryIntentActivities(intent, 0) + pm.queryIntentActivities(leanbackIntent, 0))
            .distinctBy { it.activityInfo.packageName }
            .filter { it.activityInfo.packageName != packageName }

        val savedOrder = storageManager.getSavedOrder()
        val hiddenSet = storageManager.getHiddenPackages()

        val loaded = resolved.map { info ->
            val pkg = info.activityInfo.packageName
            val orderIdx = savedOrder.indexOf(pkg).let { if (it == -1) 9999 else it }
            AppItem(
                packageName = pkg,
                label = info.loadLabel(pm).toString(),
                icon = info.loadIcon(pm),
                position = orderIdx,
                isHidden = hiddenSet.contains(pkg)
            )
        }.sortedBy { it.position }.toMutableList()

        allApps = loaded
        refreshDisplayedApps()
    }

    private fun refreshDisplayedApps() {
        displayedApps = if (showHiddenApps) {
            allApps.toMutableList()
        } else {
            allApps.filter { !it.isHidden }.toMutableList()
        }
        adapter.updateList(displayedApps)
    }

    private fun moveApp(fromPos: Int, toPos: Int) {
        if (toPos < 0 || toPos >= displayedApps.size) return
        val itemFrom = displayedApps[fromPos]
        val itemTo = displayedApps[toPos]

        val idx1 = allApps.indexOfFirst { it.packageName == itemFrom.packageName }
        val idx2 = allApps.indexOfFirst { it.packageName == itemTo.packageName }
        if (idx1 != -1 && idx2 != -1) {
            Collections.swap(allApps, idx1, idx2)
        }
        Collections.swap(displayedApps, fromPos, toPos)
        adapter.notifyItemMoved(fromPos, toPos)
        saveCurrentAppState()
    }

    private fun showAppOptionsDialog(app: AppItem) {
        val hideTitle = if (app.isHidden) "Hiện ứng dụng này" else "Ẩn ứng dụng khỏi màn hình"
        val options = arrayOf(
            "Di chuyển sang Trái",
            "Di chuyển sang Phải",
            hideTitle,
            "Gỡ cài đặt ứng dụng (Uninstall)"
        )

        AlertDialog.Builder(this)
            .setTitle(app.label)
            .setItems(options) { _, which ->
                val currentIdx = displayedApps.indexOfFirst { it.packageName == app.packageName }
                when (which) {
                    0 -> if (currentIdx > 0) moveApp(currentIdx, currentIdx - 1)
                    1 -> if (currentIdx < displayedApps.size - 1) moveApp(currentIdx, currentIdx + 1)
                    2 -> {
                        app.isHidden = !app.isHidden
                        saveCurrentAppState()
                        refreshDisplayedApps()
                    }
                    3 -> uninstallApp(app.packageName)
                }
            }
            .show()
    }

    private fun saveCurrentAppState() {
        val orderList = allApps.map { it.packageName }
        val hiddenSet = allApps.filter { it.isHidden }.map { it.packageName }.toSet()
        storageManager.saveAppConfig(orderList, hiddenSet)
    }

    private fun launchApp(pkg: String) {
        val launchIntent = packageManager.getLeanbackLaunchIntentForPackage(pkg)
            ?: packageManager.getLaunchIntentForPackage(pkg)
        if (launchIntent != null) {
            startActivity(launchIntent)
        } else {
            Toast.makeText(this, "Không thể mở ứng dụng này", Toast.LENGTH_SHORT).show()
        }
    }

    private fun uninstallApp(pkg: String) {
        val intent = Intent(Intent.ACTION_DELETE).apply {
            data = Uri.parse("package:$pkg")
        }
        startActivity(intent)
    }

    private fun loadDailyPrices() {
        lifecycleScope.launch {
            val price = PriceFetcher.fetchDailyPrices()
            binding.tvFuelPrice.text = "RON 95: ${price.fuelRon95} | Dầu DO: ${price.fuelDiesel}"
            binding.tvGoldPrice.text = "SJC Mua: ${price.goldSjcBuy} | Bán: ${price.goldSjcSell}"
            binding.tvPriceUpdated.text = "Cập nhật: ${price.updatedAt}"
        }
    }

    /**
     * Lắng nghe lệnh điều khiển từ App Remote trên điện thoại qua Firebase Realtime Database
     */
    private fun setupFirebaseListener(deviceId: String) {
        try {
            val database = FirebaseDatabase.getInstance()
            dbRef = database.getReference("devices").child(deviceId)

            // Đánh dấu trạng thái Online
            dbRef.child("status").setValue("online")
            dbRef.child("status").onDisconnect().setValue("offline")

            // Lắng nghe trạng thái Khóa TV
            dbRef.child("locked").addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val locked = snapshot.getValue(Boolean::class.java) ?: false
                    setTvLockState(locked)
                }
                override fun onCancelled(error: DatabaseError) {}
            })

            // Lắng nghe tin nhắn gửi lên TV
            dbRef.child("message").addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val text = snapshot.child("text").getValue(String::class.java)
                    val ts = snapshot.child("timestamp").getValue(Long::class.java) ?: 0L
                    if (!text.isNullOrBlank() && ts > lastCommandTimestamp) {
                        lastCommandTimestamp = ts
                        showRemoteMessageDialog(text)
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })

            // Lắng nghe lệnh điều khiển (D-Pad, Screenshot, Power Off)
            dbRef.child("command").addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val action = snapshot.child("action").getValue(String::class.java) ?: return
                    val ts = snapshot.child("timestamp").getValue(Long::class.java) ?: 0L
                    if (ts <= lastCommandTimestamp) return
                    lastCommandTimestamp = ts

                    handleRemoteAction(action)
                }
                override fun onCancelled(error: DatabaseError) {}
            })
        } catch (e: Exception) {
            Toast.makeText(this, "Chưa cấu hình google-services.json hợp lệ", Toast.LENGTH_LONG).show()
        }
    }

    private fun handleRemoteAction(action: String) {
        when (action) {
            "POWER_OFF" -> {
                Toast.makeText(this, "Đang tắt TV từ xa...", Toast.LENGTH_SHORT).show()
                RemoteCommandExecutor.turnOffTv()
            }
            "SCREENSHOT" -> {
                RemoteCommandExecutor.captureScreenBase64(this) { base64 ->
                    if (base64 != null) {
                        dbRef.child("screenshot_result").setValue(
                            mapOf(
                                "image_base64" to base64,
                                "timestamp" to System.currentTimeMillis()
                            )
                        )
                    }
                }
            }
            "DPAD_UP" -> RemoteCommandExecutor.runShellCommand("input keyevent 19")
            "DPAD_DOWN" -> RemoteCommandExecutor.runShellCommand("input keyevent 20")
            "DPAD_LEFT" -> RemoteCommandExecutor.runShellCommand("input keyevent 21")
            "DPAD_RIGHT" -> RemoteCommandExecutor.runShellCommand("input keyevent 22")
            "DPAD_CENTER" -> RemoteCommandExecutor.runShellCommand("input keyevent 23")
            "BACK" -> RemoteCommandExecutor.runShellCommand("input keyevent 4")
            "HOME" -> RemoteCommandExecutor.runShellCommand("input keyevent 3")
            "VOL_UP" -> RemoteCommandExecutor.runShellCommand("input keyevent 24")
            "VOL_DOWN" -> RemoteCommandExecutor.runShellCommand("input keyevent 25")
        }
    }

    private fun setTvLockState(locked: Boolean) {
        isTvLocked = locked
        binding.lockOverlay.visibility = if (locked) View.VISIBLE else View.GONE
    }

    private fun showRemoteMessageDialog(message: String) {
        AlertDialog.Builder(this)
            .setTitle("📩 Tin nhắn từ điện thoại")
            .setMessage(message)
            .setPositiveButton("Đóng", null)
            .show()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Chặn toàn bộ thao tác remote vật lý khi TV đang bị khóa từ xa
        if (isTvLocked) {
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onBackPressed() {
        // Không thoát khỏi Launcher khi nhấn Back ở màn hình chính
        if (isEditMode) {
            isEditMode = false
            adapter.notifyDataSetChanged()
        }
    }
}
