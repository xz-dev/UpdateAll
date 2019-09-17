package net.xzos.upgradeAll.ui.viewmodels.adapters

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.xzos.upgradeAll.R
import net.xzos.upgradeAll.server.ServerContainer
import net.xzos.upgradeAll.ui.activity.AppSettingActivity
import net.xzos.upgradeAll.ui.viewmodels.view.ItemCardView
import net.xzos.upgradeAll.ui.viewmodels.view.holder.CardViewRecyclerViewHolder
import org.json.JSONException
import java.util.*


class AppItemAdapter(private val mItemCardViewList: MutableList<ItemCardView>) : RecyclerView.Adapter<CardViewRecyclerViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardViewRecyclerViewHolder {
        val holder = CardViewRecyclerViewHolder(
                LayoutInflater.from(parent.context).inflate(R.layout.cardview_item, parent, false))
        // 单击展开 Release 详情页
        holder.itemCardView.setOnClickListener {
            showDialogWindow(holder)
        }

        // 长按强制检查版本
        holder.versionCheckButton.setOnLongClickListener {
            val position = holder.adapterPosition
            val itemCardView = mItemCardViewList[position]
            val appDatabaseId = itemCardView.extraData.databaseId
            AppManager.delApp(appDatabaseId)
            AppManager.setApp(appDatabaseId)
            setAppStatusUI(appDatabaseId, holder)
            Toast.makeText(holder.versionCheckButton.context, String.format("检查 %s 的更新", holder.name.text.toString()),
                    Toast.LENGTH_SHORT).show()
            true
        }

        // 打开指向Url
        holder.descTextView.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = Uri.parse(holder.descTextView.text.toString())
            val chooser = Intent.createChooser(intent, "请选择浏览器")
            if (intent.resolveActivity(holder.descTextView.context.packageManager) != null) {
                holder.descTextView.context.startActivity(chooser)
            }
        }
        return holder
    }

    override fun onBindViewHolder(holder: CardViewRecyclerViewHolder, position: Int) {
        val itemCardView = mItemCardViewList[position]
        // 底栏设置
        if (itemCardView.extraData.isEmpty) {
            holder.itemCardView.visibility = View.GONE
            holder.endTextView.visibility = View.VISIBLE
        } else {
            holder.itemCardView.visibility = View.VISIBLE
            holder.endTextView.visibility = View.GONE
            val appDatabaseId = itemCardView.extraData.databaseId
            holder.name.text = itemCardView.name
            holder.api.text = itemCardView.api
            holder.descTextView.text = itemCardView.desc
            setAppStatusUI(appDatabaseId, holder)
        }
    }

    fun onAddItem(position: Int = 0, element: ItemCardView) {
        if (position < mItemCardViewList.size) {
            mItemCardViewList.add(position, element)
            notifyItemRangeChanged(position, itemCount)
        }
    }

    fun onItemDismiss(position: Int): ItemCardView? {
        return if (position != -1 && position < mItemCardViewList.size) {
            val removedItemCardView = mItemCardViewList[position]
            mItemCardViewList.removeAt(position)
            notifyItemRemoved(position)
            notifyItemRangeChanged(position, itemCount)
            removedItemCardView
        } else
            null
    }

    fun onItemMove(fromPosition: Int, toPosition: Int): Boolean {
        if (fromPosition < toPosition) {
            for (i in fromPosition until toPosition) {
                Collections.swap(mItemCardViewList, i, i + 1)
            }
        } else {
            for (i in fromPosition downTo toPosition + 1) {
                Collections.swap(mItemCardViewList, i, i - 1)
            }
        }
        notifyItemMoved(fromPosition, toPosition)
        return true
    }

    override fun getItemCount(): Int {
        return mItemCardViewList.size
    }

    private fun setAppStatusUI(appDatabaseId: Long, holder: CardViewRecyclerViewHolder) {
        val app = AppManager.getApp(appDatabaseId)
        val updater = app.updater
        setUpdateStatus(holder, true)
        GlobalScope.launch {
            val updateStatus =   // 0: 404; 1: latest; 2: need update; 3: no app
                    //检查是否取得云端版本号
                    if (updater.isSuccessRenew) {
                        // 检查是否获取本地版本号
                        if (app.installedVersion != null) {
                            // 检查本地版本
                            if (app.isLatest) {
                                1
                            } else {
                                2
                            }
                        } else {
                            3
                        }
                    } else {
                        0
                    }
            Handler(Looper.getMainLooper()).post {
                when (updateStatus) {
                    0 -> holder.versionCheckButton.setImageResource(R.drawable.ic_404)
                    1 -> holder.versionCheckButton.setImageResource(R.drawable.ic_check_latest)
                    2 -> holder.versionCheckButton.setImageResource(R.drawable.ic_check_needupdate)
                    3 -> holder.versionCheckButton.setImageResource(R.drawable.ic_local_error)
                }
                setUpdateStatus(holder, false)
            }
        }
    }

    private fun setUpdateStatus(holder: CardViewRecyclerViewHolder, renew: Boolean) {
        if (renew) {
            holder.versionCheckButton.visibility = View.INVISIBLE
            holder.versionCheckingBar.visibility = View.VISIBLE
        } else {
            holder.versionCheckButton.visibility = View.VISIBLE
            holder.versionCheckingBar.visibility = View.INVISIBLE
        }
    }

    private fun showDialogWindow(holder: CardViewRecyclerViewHolder) {
        val position = holder.adapterPosition
        val itemCardView = mItemCardViewList[position]
        val appDatabaseId = itemCardView.extraData.databaseId
        val app = AppManager.getApp(appDatabaseId)
        val updater = app.updater
        val builder = AlertDialog.Builder(holder.versionCheckingBar.context)

        val dialog = builder.setView(R.layout.dialog_app_info).create()
        dialog.show()
        val dialogWindow = dialog.window
        if (dialogWindow != null) {
            val editButton = dialogWindow.findViewById<Button>(R.id.editButton)
            editButton.setOnClickListener {
                // 修改按钮
                val intent = Intent(holder.itemCardView.context, AppSettingActivity::class.java)
                intent.putExtra("database_id", appDatabaseId)
                holder.itemCardView.context.startActivity(intent)
            }
            val localReleaseTextView = dialogWindow.findViewById<TextView>(R.id.localReleaseTextView)
            // 显示本地版本号
            val installedVersion = app.installedVersion
            if (installedVersion != null)
                localReleaseTextView.text = installedVersion
            else
                localReleaseTextView.text = "获取失败"

            GlobalScope.launch {
                val latestVersionString = updater.latestVersion
                Handler(Looper.getMainLooper()).post {
                    val cloudReleaseTextView = dialogWindow.findViewById<TextView>(R.id.cloudReleaseTextView)
                    cloudReleaseTextView.text = latestVersionString
                    dialogWindow.findViewById<View>(R.id.cloudReleaseProgressBar).visibility = View.GONE// 隐藏等待提醒条
                }
                val latestVersionChangelogString = updater.latestChangelog
                Handler(Looper.getMainLooper()).post {
                    if (latestVersionChangelogString.isNullOrBlank()) {
                        dialogWindow.findViewById<View>(R.id.releaseChangelogLinearLayout).visibility = View.GONE
                    } else {
                        val changelogTextView = dialogWindow.findViewById<TextView>(R.id.changelogTextView)
                        changelogTextView.text = latestVersionChangelogString
                        dialogWindow.findViewById<View>(R.id.changelogProgressBar).visibility = View.GONE// 隐藏等待提醒条
                    }
                }
                val latestFileDownloadUrl = updater.latestDownloadUrl
                Handler(Looper.getMainLooper()).post {
                    val itemList = ArrayList<String>()
                    val sIterator = latestFileDownloadUrl.keys()
                    while (sIterator.hasNext()) {
                        val key = sIterator.next()
                        itemList.add(key)
                    }

                    // 无Release文件，不显示网络文件列表
                    if (itemList.size == 0) {
                        dialogWindow.findViewById<View>(R.id.releaseFileListLinearLayout).visibility = View.GONE
                    } else {

                        // 构建文件列表
                        val adapter = ArrayAdapter(
                                dialog.context, android.R.layout.simple_list_item_1, itemList)
                        val cloudReleaseList = dialogWindow.findViewById<ListView>(R.id.cloudReleaseList)
                        // 设置文件列表点击事件
                        cloudReleaseList.setOnItemClickListener { _, _, i, _ ->
                            val intent = Intent(Intent.ACTION_VIEW)
                            var url: String? = null
                            try {
                                url = latestFileDownloadUrl.getString(itemList[i])
                            } catch (e: JSONException) {
                                e.printStackTrace()
                            }

                            if (url != null && !url.startsWith("http"))
                                url = "http://$url"
                            intent.data = Uri.parse(url)
                            val chooser = Intent.createChooser(intent, "请选择浏览器")
                            if (intent.resolveActivity(dialog.context.packageManager) != null) {
                                dialog.context.startActivity(chooser)
                            }
                        }
                        cloudReleaseList.adapter = adapter
                        dialogWindow.findViewById<View>(R.id.fileListProgressBar).visibility = View.GONE// 隐藏等待提醒条
                    }
                }
            }
        }
    }

    companion object {
        private val AppManager = ServerContainer.AppManager
    }
}