package com.yunshu.txtreader;

import android.content.Context;
import android.os.Environment;
import android.webkit.JavascriptInterface;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 文件桥接接口 —— 让 HTML/JS 能读写手机文件系统
 * 支持两个文件夹：
 *   1. Documents/TXTReader/ — 主文件夹
 *   2. Download/BaiduNetdisk/_pcs_.workspace/QCLAW/小说/ — 云竹小说文件夹
 */
public class FileBridge {

    private final Context context;

    public FileBridge(Context context) {
        this.context = context;
    }

    /** 主文件夹：Documents/TXTReader/ */
    private File getMainDir() {
        File dir = new File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            "TXTReader"
        );
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    /** 云竹小说文件夹 */
    private File getNovelDir() {
        File dir = new File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "BaiduNetdisk/_pcs_.workspace/QCLAW/小说"
        );
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    /** 检查路径是否在允许的目录下 */
    private boolean isAllowedPath(String path) {
        String absPath = new File(path).getAbsolutePath();
        return absPath.startsWith(getMainDir().getAbsolutePath())
            || absPath.startsWith(getNovelDir().getAbsolutePath());
    }

    /** 扫描一个目录下的 txt 文件，返回 JSON 片段 */
    private void scanDir(File dir, String group, StringBuilder sb, boolean[] first) {
        File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".txt"));
        if (files == null) return;

        List<File> fileList = new ArrayList<>();
        Collections.addAll(fileList, files);
        Collections.sort(fileList, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));

        for (File f : fileList) {
            if (!first[0]) sb.append(",");
            first[0] = false;
            sb.append("{\"name\":\"").append(escapeJson(f.getName()))
              .append("\",\"size\":").append(f.length())
              .append(",\"path\":\"").append(escapeJson(f.getAbsolutePath()))
              .append("\",\"group\":\"").append(escapeJson(group)).append("\"}");
        }
    }

    /** 列出所有 .txt 文件，返回 JSON 数组（带 group 分组） */
    @JavascriptInterface
    public String listFiles() {
        StringBuilder sb = new StringBuilder("[");
        boolean[] first = {true};
        scanDir(getMainDir(), "主文件夹", sb, first);
        scanDir(getNovelDir(), "云竹小说", sb, first);
        sb.append("]");
        return sb.toString();
    }

    /** 获取主文件夹路径 */
    @JavascriptInterface
    public String getMainDirPath() {
        return getMainDir().getAbsolutePath();
    }

    /** 获取小说文件夹路径 */
    @JavascriptInterface
    public String getNovelDirPath() {
        return getNovelDir().getAbsolutePath();
    }

    /** 读取文件内容 */
    @JavascriptInterface
    public String readFile(String filePath) {
        try {
            File file = new File(filePath);
            if (!file.exists()) return "__ERROR__文件不存在";
            if (!isAllowedPath(filePath)) return "__ERROR__无权访问该文件";

            InputStreamReader reader = new InputStreamReader(new FileInputStream(file), "UTF-8");
            char[] buffer = new char[8192];
            StringBuilder sb = new StringBuilder();
            int len;
            while ((len = reader.read(buffer)) != -1) {
                sb.append(buffer, 0, len);
            }
            reader.close();
            return sb.toString();
        } catch (Exception e) {
            return "__ERROR__" + e.getMessage();
        }
    }

    /** 保存文件（覆盖写入） */
    @JavascriptInterface
    public String saveFile(String filePath, String content) {
        try {
            File file = new File(filePath);
            if (!isAllowedPath(filePath)) return "__ERROR__无权写入该位置";

            OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file), "UTF-8");
            writer.write(content);
            writer.flush();
            writer.close();
            return "__OK__";
        } catch (Exception e) {
            return "__ERROR__" + e.getMessage();
        }
    }

    /** 新建文件（group: "main" 或 "novel"） */
    @JavascriptInterface
    public String createFile(String fileName, String group) {
        try {
            if (!fileName.toLowerCase().endsWith(".txt")) {
                fileName += ".txt";
            }
            File dir = "novel".equals(group) ? getNovelDir() : getMainDir();
            File file = new File(dir, fileName);
            if (file.exists()) return "__ERROR__文件已存在";

            OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file), "UTF-8");
            writer.write("");
            writer.close();
            return file.getAbsolutePath();
        } catch (Exception e) {
            return "__ERROR__" + e.getMessage();
        }
    }

    /** 删除文件 */
    @JavascriptInterface
    public String deleteFile(String filePath) {
        try {
            File file = new File(filePath);
            if (!isAllowedPath(filePath)) return "__ERROR__无权删除该文件";
            if (file.delete()) return "__OK__";
            return "__ERROR__删除失败";
        } catch (Exception e) {
            return "__ERROR__" + e.getMessage();
        }
    }

    /** 重命名文件 */
    @JavascriptInterface
    public String renameFile(String oldPath, String newName) {
        try {
            File oldFile = new File(oldPath);
            if (!isAllowedPath(oldPath)) return "__ERROR__无权操作该文件";
            if (!newName.toLowerCase().endsWith(".txt")) newName += ".txt";

            File dir = oldFile.getParentFile();
            File newFile = new File(dir, newName);
            if (newFile.exists()) return "__ERROR__目标文件已存在";
            if (oldFile.renameTo(newFile)) return newFile.getAbsolutePath();
            return "__ERROR__重命名失败";
        } catch (Exception e) {
            return "__ERROR__" + e.getMessage();
        }
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
