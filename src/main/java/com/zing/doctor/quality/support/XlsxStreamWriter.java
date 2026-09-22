package com.zing.doctor.quality.support;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 零依赖的流式 xlsx 写出器。
 *
 * <p>为什么不用 POI / EasyExcel：本项目 P0 要求「不新增依赖、不改 pom」，而 xlsx 本质是
 * 一组 XML 打成的 zip。这里直接用 JDK 自带的 {@link ZipOutputStream} 边写边压：
 * <ul>
 *   <li><b>不占内存</b>：行数据逐行写进 zip 流，导出 10 万行也不 OOM（POI 全内存模型会）</li>
 *   <li><b>零依赖</b>：只需要 JDK，交付物不变重</li>
 *   <li><b>够用</b>：支持多 sheet、表头、字符串与数值（质控报表只需要这两类单元格）</li>
 * </ul>
 *
 * <p>用法（必须先声明全部 sheet 名，再按顺序写）：
 * <pre>
 *   try (XlsxStreamWriter w = new XlsxStreamWriter(out, "月度汇总", "指标清单")) {
 *       w.beginSheet(0, new String[]{"指标编号", "1月"});
 *       w.writeRow(new Object[]{"quality_0", 12});
 *       w.endSheet();
 *       ...
 *   }
 * </pre>
 */
public class XlsxStreamWriter implements Closeable {

    private final ZipOutputStream zos;
    private final String[] sheetNames;
    private boolean sheetOpen = false;
    /** 当前 sheet 内已写行数（含表头），用于生成 r 引用。 */
    private int rowIndex = 0;

    public XlsxStreamWriter(OutputStream out, String... sheetNames) throws IOException {
        this.zos = new ZipOutputStream(out, StandardCharsets.UTF_8);
        this.sheetNames = sheetNames == null || sheetNames.length == 0
                ? new String[]{"Sheet1"} : sheetNames;
        writeMeta();
    }

    /** 开始写第 index 个 sheet（与构造时的名称顺序一致）。 */
    public void beginSheet(int index, String[] headers) throws IOException {
        if (sheetOpen) {
            throw new IllegalStateException("上一个 sheet 未调用 endSheet()");
        }
        if (index < 0 || index >= sheetNames.length) {
            throw new IllegalArgumentException("sheet 下标越界: " + index);
        }
        sheetOpen = true;
        rowIndex = 0;
        entry("xl/worksheets/sheet" + (index + 1) + ".xml");
        StringBuilder sb = new StringBuilder(512);
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
        sb.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">");
        sb.append("<sheetData>");
        raw(sb.toString());
        if (headers != null && headers.length > 0) {
            writeRow(headers);
        }
    }

    /** 写一行数据。null 写空单元格；Number 写数值；其余按文本写。 */
    public void writeRow(Object[] cells) throws IOException {
        if (!sheetOpen) {
            throw new IllegalStateException("未调用 beginSheet()");
        }
        rowIndex++;
        StringBuilder sb = new StringBuilder(256);
        sb.append("<row r=\"").append(rowIndex).append("\">");
        for (int i = 0; i < cells.length; i++) {
            Object v = cells[i];
            if (v == null) {
                continue;
            }
            String ref = colRef(i) + rowIndex;
            if (v instanceof Number) {
                sb.append("<c r=\"").append(ref).append("\"><v>").append(v).append("</v></c>");
            } else if (v instanceof Boolean) {
                sb.append("<c r=\"").append(ref).append("\" t=\"b\"><v>")
                        .append(Boolean.TRUE.equals(v) ? 1 : 0).append("</v></c>");
            } else {
                sb.append("<c r=\"").append(ref).append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                        .append(escape(String.valueOf(v))).append("</t></is></c>");
            }
        }
        sb.append("</row>");
        raw(sb.toString());
    }

    public void endSheet() throws IOException {
        raw("</sheetData></worksheet>");
        zos.closeEntry();
        sheetOpen = false;
    }

    @Override
    public void close() throws IOException {
        if (sheetOpen) {
            endSheet();
        }
        zos.finish();
        zos.close();
    }

    // ------------------------------------------------------------------
    // OOXML 骨架
    // ------------------------------------------------------------------

    private void writeMeta() throws IOException {
        entry("[Content_Types].xml");
        StringBuilder ct = new StringBuilder();
        ct.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
        ct.append("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">");
        ct.append("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>");
        ct.append("<Default Extension=\"xml\" ContentType=\"application/xml\"/>");
        ct.append("<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>");
        for (int i = 0; i < sheetNames.length; i++) {
            ct.append("<Override PartName=\"/xl/worksheets/sheet").append(i + 1)
                    .append(".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>");
        }
        ct.append("</Types>");
        raw(ct.toString());

        entry("_rels/.rels");
        raw("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"
                + "</Relationships>");

        entry("xl/workbook.xml");
        StringBuilder wb = new StringBuilder();
        wb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
        wb.append("<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" ")
                .append("xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">");
        wb.append("<sheets>");
        for (int i = 0; i < sheetNames.length; i++) {
            wb.append("<sheet name=\"").append(escape(sheetNames[i]))
                    .append("\" sheetId=\"").append(i + 1)
                    .append("\" r:id=\"rId").append(i + 1).append("\"/>");
        }
        wb.append("</sheets></workbook>");
        raw(wb.toString());

        entry("xl/_rels/workbook.xml.rels");
        StringBuilder rel = new StringBuilder();
        rel.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
        rel.append("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">");
        for (int i = 0; i < sheetNames.length; i++) {
            rel.append("<Relationship Id=\"rId").append(i + 1)
                    .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" ")
                    .append("Target=\"worksheets/sheet").append(i + 1).append(".xml\"/>");
        }
        rel.append("</Relationships>");
        raw(rel.toString());
    }

    private void entry(String name) throws IOException {
        zos.putNextEntry(new ZipEntry(name));
    }

    private void raw(String xml) throws IOException {
        zos.write(xml.getBytes(StandardCharsets.UTF_8));
    }

    /** 0 → A, 25 → Z, 26 → AA。 */
    static String colRef(int index) {
        StringBuilder sb = new StringBuilder();
        int n = index;
        while (n >= 0) {
            sb.insert(0, (char) ('A' + n % 26));
            n = n / 26 - 1;
        }
        return sb.toString();
    }

    static String escape(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&':
                    sb.append("&amp;");
                    break;
                case '<':
                    sb.append("&lt;");
                    break;
                case '>':
                    sb.append("&gt;");
                    break;
                case '"':
                    sb.append("&quot;");
                    break;
                case '\'':
                    sb.append("&apos;");
                    break;
                default:
                    // 剔除 XML 非法控制字符，避免生成的 xlsx 打不开
                    if (c < 0x20 && c != '\t' && c != '\n' && c != '\r') {
                        break;
                    }
                    sb.append(c);
            }
        }
        return sb.toString();
    }
}
