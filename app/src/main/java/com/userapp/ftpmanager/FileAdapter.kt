package com.userapp.ftpmanager

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.PopupMenu
import android.widget.TextView
import org.apache.commons.net.ftp.FTPFile

interface FileActionListener {
    fun onOpenFolder(f: FTPFile)
    fun onSelect(f: FTPFile)
    fun onDownload(f: FTPFile)
    fun onDelete(f: FTPFile)
    fun onRename(f: FTPFile)
    fun onMove(f: FTPFile)
    fun onCopy(f: FTPFile)
}

class FileAdapter(
    private val context: Context,
    private var items: List<FTPFile>,
    private val listener: FileActionListener
) : BaseAdapter() {

    fun update(newItems: List<FTPFile>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun getCount(): Int = items.size
    override fun getItem(position: Int): FTPFile = items[position]
    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_file, parent, false)
        val f = items[position]

        val tvIcon = view.findViewById<TextView>(R.id.tvIcon)
        val tvName = view.findViewById<TextView>(R.id.tvName)
        val btnMore = view.findViewById<TextView>(R.id.btnMore)

        tvIcon.text = if (f.isDirectory) "📁" else "📄"
        tvName.text = f.name

        view.setOnClickListener {
            if (f.isDirectory) listener.onOpenFolder(f) else listener.onSelect(f)
        }

        btnMore.setOnClickListener { anchor ->
            val popup = PopupMenu(context, anchor)
            if (f.isDirectory) {
                popup.menu.add(0, 1, 0, "İçine Gir")
            } else {
                popup.menu.add(0, 2, 0, "İndir")
            }
            popup.menu.add(0, 3, 0, "Yeniden Adlandır")
            popup.menu.add(0, 4, 0, "Taşı")
            popup.menu.add(0, 5, 0, "Kopyala")
            popup.menu.add(0, 6, 0, "Sil")
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> listener.onOpenFolder(f)
                    2 -> listener.onDownload(f)
                    3 -> listener.onRename(f)
                    4 -> listener.onMove(f)
                    5 -> listener.onCopy(f)
                    6 -> listener.onDelete(f)
                }
                true
            }
            popup.show()
        }

        return view
    }
}
