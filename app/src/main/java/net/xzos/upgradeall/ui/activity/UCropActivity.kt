package net.xzos.upgradeall.ui.activity

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.sync.Mutex
import net.xzos.dupdatesystem.core.data.json.nongson.ObjectTag
import net.xzos.dupdatesystem.core.data_manager.utils.wait
import net.xzos.dupdatesystem.core.log.Log
import net.xzos.upgradeall.R
import net.xzos.upgradeall.utils.FileUtil
import java.io.File

class UCropActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_blank_wait)
        readPic()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        super.onActivityResult(requestCode, resultCode, resultData)
        when (resultCode) {
            Activity.RESULT_OK -> {
                when (requestCode) {
                    READ_PIC_REQUEST_CODE -> {
                        val uri = resultData?.data
                        if (uri != null) {
                            val parent = FILE.parentFile
                            if (parent != null && !parent.exists())
                                parent.mkdirs()
                            val destinationUri = Uri.fromFile(FILE)
                            UCrop.of(FileUtil.imageUriDump(uri, this), destinationUri)
                                    .withAspectRatio(x, y)
                                    .start(this, UCrop.REQUEST_CROP)
                        }
                    }
                    UCrop.REQUEST_CROP -> {
                        isSuccess = true
                        finish()
                    }
                    else -> finish()
                }
            }
            Activity.RESULT_CANCELED -> {
                finish()
            }
            UCrop.RESULT_ERROR -> {
                val cropError = UCrop.getError(resultData!!)
                if (cropError != null)
                    Log.e(logObjectTag, TAG, "onActivityResult: 图片裁剪错误: $cropError")
                Toast.makeText(this, R.string.ucrop_error, Toast.LENGTH_LONG).show()
                finish()
            }
            else -> finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 清除图片缓存
        if (FILE != cacheImageFile)
            cacheImageFile.delete()
        // 运行完成，解锁
        if (mutex.isLocked) mutex.unlock()
    }

    private fun readPic() {
        if (FileUtil.requestPermission(this, PERMISSIONS_REQUEST_WRITE_CONTACTS)) {
            FileUtil.getPicFormGallery(this, READ_PIC_REQUEST_CODE)
        }
    }

    override fun onRequestPermissionsResult(
            requestCode: Int,
            permissions: Array<String>, grantResults: IntArray
    ) {
        if (requestCode == PERMISSIONS_REQUEST_WRITE_CONTACTS) {
            if (grantResults.isEmpty() || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, R.string.file_permission_request, Toast.LENGTH_LONG).show()
                finish()
            } else {
                readPic()
            }
        }
    }

    companion object {
        private const val TAG = "UCropActivity"
        private val logObjectTag = ObjectTag("UI", TAG)

        private const val PERMISSIONS_REQUEST_WRITE_CONTACTS = 1
        private const val READ_PIC_REQUEST_CODE = 2
        private val cacheImageFile = FileUtil.IMAGE_CACHE_FILE

        private val mutex = Mutex()

        private var isSuccess = false

        private var FILE: File = File("")
        private var x = 0f
        private var y = 0f

        suspend fun newInstance(x: Float, y: Float, file: File, context: Context): Boolean {
            mutex.lock()
            isSuccess = false
            FILE = file
            this.x = x
            this.y = y
            context.startActivity(Intent(context, UCropActivity::class.java))
            mutex.wait()
            FILE = File("")
            return isSuccess
        }
    }
}
