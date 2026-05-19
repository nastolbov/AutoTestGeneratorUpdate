package ru.autotestgen.generator;

import ru.autotestgen.model.TestCaseResult;
import ru.autotestgen.model.TestRunResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes a standalone HTML run report — the "max signal per run" artifact:
 * per-test screenshot strip, captured stdout, parsed search params, step timings,
 * failure messages. Plus a flat CSV beside it for Excel ingestion.
 */
public class RunReportWriter {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public void write(Path htmlPath, TestRunResult result) throws IOException {
        StringBuilder h = new StringBuilder();
        h.append("<!DOCTYPE html>\n<html lang=\"ru\"><head><meta charset=\"UTF-8\">\n");
        h.append("<title>AutoTestGenerator — run report</title>\n");
        h.append("<style>\n");
        h.append("  body { font-family: -apple-system,Segoe UI,Roboto,sans-serif; margin:24px; color:#222; background:#fafafa; }\n");
        h.append("  h1 { margin:0 0 4px 0; }\n");
        h.append("  .meta { color:#666; margin-bottom:16px; }\n");
        h.append("  .summary { display:flex; gap:24px; margin:16px 0; }\n");
        h.append("  .pill { padding:8px 14px; border-radius:8px; font-weight:600; }\n");
        h.append("  .ok { background:#e8f7ee; color:#1d7a3a; }\n");
        h.append("  .fail { background:#fdecea; color:#a8281b; }\n");
        h.append("  .skip { background:#f0eef9; color:#5a428a; }\n");
        h.append("  .total { background:#eef1f5; color:#3b4252; }\n");
        h.append("  details { background:#fff; border:1px solid #e1e4e8; border-radius:8px; padding:0 12px; margin:8px 0; }\n");
        h.append("  details > summary { cursor:pointer; padding:12px 0; font-weight:600; list-style:none; display:flex; gap:10px; align-items:center; }\n");
        h.append("  details > summary::-webkit-details-marker { display:none; }\n");
        h.append("  .status-dot { width:10px; height:10px; border-radius:50%; display:inline-block; }\n");
        h.append("  .dot-ok { background:#28a745; } .dot-fail { background:#dc3545; } .dot-skip { background:#6c757d; }\n");
        h.append("  .time { color:#888; font-weight:normal; font-size:0.9em; margin-left:auto; }\n");
        h.append("  .shotstrip { display:flex; gap:8px; flex-wrap:wrap; padding:8px 0; }\n");
        h.append("  .shotstrip img { width:180px; height:auto; border:1px solid #ddd; border-radius:4px; cursor:pointer; }\n");
        h.append("  .shotstrip .stepname { font-size:0.75em; color:#666; text-align:center; margin-top:2px; }\n");
        h.append("  .shotbox { display:flex; flex-direction:column; align-items:center; }\n");
        h.append("  pre { background:#f6f8fa; padding:10px; border-radius:6px; overflow-x:auto; font-size:0.85em; }\n");
        h.append("  .failmsg { color:#a8281b; background:#fdecea; padding:8px 12px; border-radius:6px; margin:8px 0; }\n");
        h.append("  table.params { border-collapse:collapse; margin:8px 0; font-size:0.9em; }\n");
        h.append("  table.params td { border:1px solid #ddd; padding:4px 10px; }\n");
        h.append("  table.params td:first-child { font-weight:600; background:#f6f8fa; }\n");
        h.append("  table.steps { border-collapse:collapse; margin:8px 0; font-size:0.9em; }\n");
        h.append("  table.steps td { padding:2px 8px; }\n");
        h.append("  table.steps td:last-child { text-align:right; color:#666; }\n");
        h.append("  .section-title { color:#333; font-size:0.95em; margin:12px 0 4px 0; font-weight:600; }\n");
        h.append("  .empty { color:#999; font-style:italic; font-size:0.9em; }\n");
        h.append("</style></head><body>\n");

        h.append("<h1>AutoTestGenerator — отчёт по прогону</h1>\n");
        h.append("<div class=\"meta\">");
        if (result.getRunTimestamp() != null) h.append("Запущен: ").append(TS.format(result.getRunTimestamp())).append(" · ");
        h.append("XML: ").append(esc(result.getXmlFileName()))
                .append(" · URL: ").append(esc(result.getBaseUrl()))
                .append(" · Длительность: ").append(formatDuration(result.getDurationMs()));
        h.append("</div>\n");

        h.append("<div class=\"summary\">\n");
        h.append("  <div class=\"pill total\">Всего: ").append(result.getTotalTests()).append("</div>\n");
        h.append("  <div class=\"pill ok\">✓ ").append(result.getPassed()).append("</div>\n");
        h.append("  <div class=\"pill fail\">✗ ").append(result.getFailed()).append("</div>\n");
        h.append("  <div class=\"pill skip\">⏭ ").append(result.getSkipped()).append("</div>\n");
        h.append("</div>\n");

        // Group test cases by class
        Map<String, java.util.List<TestCaseResult>> byClass = new LinkedHashMap<>();
        for (TestCaseResult tc : result.getResults()) {
            String cls = tc.getClassName() == null ? "(no class)" : tc.getClassName();
            byClass.computeIfAbsent(cls, k -> new java.util.ArrayList<>()).add(tc);
        }

        for (Map.Entry<String, List<TestCaseResult>> entry : byClass.entrySet()) {
            String cls = entry.getKey();
            List<TestCaseResult> tests = entry.getValue();
            long passed = tests.stream().filter(t -> t.isPassed() && !t.isSkipped()).count();
            long failed = tests.stream().filter(t -> !t.isPassed() && !t.isSkipped()).count();
            long skipped = tests.stream().filter(TestCaseResult::isSkipped).count();
            long total = tests.size();
            long classDur = tests.stream().mapToLong(TestCaseResult::getDurationMs).sum();

            h.append("<details open><summary>");
            h.append("<strong>").append(esc(shortName(cls))).append("</strong> ");
            h.append("<span style=\"color:#666; font-weight:normal\">— ").append(total).append(" тестов: ")
                    .append(passed).append(" ✓ ").append(failed).append(" ✗ ").append(skipped).append(" ⏭</span>");
            h.append("<span class=\"time\">").append(formatDuration(classDur)).append("</span>");
            h.append("</summary>\n");

            for (TestCaseResult tc : tests) {
                String dotCls;
                String statusLabel;
                if (tc.isSkipped()) { dotCls = "dot-skip"; statusLabel = "SKIP"; }
                else if (tc.isPassed()) { dotCls = "dot-ok"; statusLabel = "PASS"; }
                else { dotCls = "dot-fail"; statusLabel = "FAIL"; }
                h.append("<details><summary>");
                h.append("<span class=\"status-dot ").append(dotCls).append("\"></span>");
                h.append("<span>").append(esc(tc.getMethodName())).append("</span>");
                h.append("<span style=\"color:#888; font-size:0.85em\">").append(statusLabel).append("</span>");
                h.append("<span class=\"time\">").append(formatDuration(tc.getDurationMs())).append("</span>");
                h.append("</summary>\n");

                if (tc.getFailureMessage() != null && !tc.getFailureMessage().isEmpty()) {
                    h.append("<div class=\"failmsg\">").append(esc(tc.getFailureMessage())).append("</div>\n");
                }

                if (!tc.getScreenshots().isEmpty()) {
                    h.append("<div class=\"section-title\">Скриншоты (по шагам):</div>\n");
                    h.append("<div class=\"shotstrip\">\n");
                    java.util.List<String> shots = new java.util.ArrayList<>(tc.getScreenshots());
                    java.util.Collections.sort(shots);
                    for (String s : shots) {
                        String rel = htmlPath.getParent() == null ? s : htmlPath.getParent().relativize(Path.of(s)).toString();
                        String name = Path.of(s).getFileName().toString();
                        String stepLabel = extractStepLabel(name);
                        h.append("  <div class=\"shotbox\"><a href=\"").append(esc(rel)).append("\" target=\"_blank\">")
                                .append("<img src=\"").append(esc(rel)).append("\" alt=\"").append(esc(stepLabel)).append("\"></a>")
                                .append("<div class=\"stepname\">").append(esc(stepLabel)).append("</div></div>\n");
                    }
                    h.append("</div>\n");
                }

                if (!tc.getSearchParams().isEmpty()) {
                    h.append("<div class=\"section-title\">Параметры поиска (что передавали в форму):</div>\n");
                    h.append("<table class=\"params\">\n");
                    for (Map.Entry<String, String> e : tc.getSearchParams().entrySet()) {
                        h.append("  <tr><td>").append(esc(e.getKey())).append("</td><td>").append(esc(e.getValue())).append("</td></tr>\n");
                    }
                    h.append("</table>\n");
                }

                if (!tc.getSteps().isEmpty()) {
                    h.append("<div class=\"section-title\">Время по шагам:</div>\n");
                    h.append("<table class=\"steps\">\n");
                    for (TestCaseResult.StepTiming st : tc.getSteps()) {
                        h.append("  <tr><td>").append(esc(st.name)).append("</td><td>").append(st.ms).append(" ms</td></tr>\n");
                    }
                    h.append("</table>\n");
                }

                if (tc.getStdOut() != null && !tc.getStdOut().isEmpty()) {
                    h.append("<details><summary class=\"section-title\">stdout (полный)</summary>\n");
                    h.append("<pre>").append(esc(tc.getStdOut())).append("</pre>\n");
                    h.append("</details>\n");
                }

                h.append("</details>\n");
            }
            h.append("</details>\n");
        }

        h.append("</body></html>\n");
        Files.writeString(htmlPath, h.toString(), StandardCharsets.UTF_8);
        System.out.println("Run report: " + htmlPath);
    }

    /** Flat CSV — one line per test case — for Excel/Sheets ingestion. */
    public void writeCsv(Path csvPath, TestRunResult result) throws IOException {
        StringBuilder s = new StringBuilder();
        s.append("class,test,status,duration_ms,search_params,step_count,screenshot_count,failure_msg\n");
        for (TestCaseResult tc : result.getResults()) {
            String status = tc.isSkipped() ? "SKIP" : (tc.isPassed() ? "PASS" : "FAIL");
            String params = tc.getSearchParams().entrySet().stream()
                    .map(e -> e.getKey() + "=" + e.getValue())
                    .reduce((a, b) -> a + "; " + b).orElse("");
            s.append(csv(tc.getClassName())).append(",")
                    .append(csv(tc.getMethodName())).append(",")
                    .append(status).append(",")
                    .append(tc.getDurationMs()).append(",")
                    .append(csv(params)).append(",")
                    .append(tc.getSteps().size()).append(",")
                    .append(tc.getScreenshots().size()).append(",")
                    .append(csv(tc.getFailureMessage())).append("\n");
        }
        Files.writeString(csvPath, s.toString(), StandardCharsets.UTF_8);
    }

    private static String csv(String v) {
        if (v == null) return "";
        if (v.contains(",") || v.contains("\"") || v.contains("\n")) {
            return "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static String shortName(String cls) {
        if (cls == null) return "";
        int dot = cls.lastIndexOf('.');
        return dot < 0 ? cls : cls.substring(dot + 1);
    }

    /** Pulls "step_status" out of "EntityClass_testMethod_NN_step_status.png". */
    private static String extractStepLabel(String filename) {
        String base = filename.endsWith(".png") ? filename.substring(0, filename.length() - 4) : filename;
        String[] parts = base.split("_");
        if (parts.length >= 5) {
            // join everything from index 3 (step) to end (which may include status)
            StringBuilder sb = new StringBuilder();
            for (int i = 3; i < parts.length; i++) {
                if (sb.length() > 0) sb.append(" ");
                sb.append(parts[i]);
            }
            return sb.toString();
        }
        return base;
    }

    private static String formatDuration(long ms) {
        if (ms < 1000) return ms + " ms";
        long s = ms / 1000;
        if (s < 60) return s + "." + ((ms % 1000) / 100) + " s";
        long m = s / 60;
        return m + " m " + (s % 60) + " s";
    }
}
