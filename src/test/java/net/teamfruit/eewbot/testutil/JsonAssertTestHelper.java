package net.teamfruit.eewbot.testutil;

import org.json.JSONException;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * JSONAssertのテスト失敗時に、期待値・実際の値・差分をファイルにダンプするヘルパークラス
 */
public class JsonAssertTestHelper {

    /**
     * ダンプファイルの出力ディレクトリ（build/test-failures/json-dumps/）
     */
    private static final Path DUMP_ROOT_DIR = Paths.get("build/test-failures/json-dumps");

    /**
     * JSONAssertを実行し、失敗時にファイルダンプを行う
     *
     * @param expected       期待値JSON
     * @param actual         実際の値JSON
     * @param compareMode    比較モード
     * @param testClassName  テストクラス名（例: "VXSE51WebhookTest"）
     * @param testMethodName テストメソッド名（例: "testDiscordWebhookJson"）
     * @param caseName       テストケース名（例: "case1"）
     * @throws JSONException JSONAssertが失敗した場合（ダンプ後に再スロー）
     */
    public static void assertJsonWithDump(
            String expected,
            String actual,
            JSONCompareMode compareMode,
            String testClassName,
            String testMethodName,
            String caseName
    ) throws JSONException {
        try {
            JSONAssert.assertEquals(expected, actual, compareMode);
        } catch (AssertionError e) {
            // ダンプ処理（エラーが発生してもテスト失敗に影響させない）
            try {
                dumpJsonComparisonFailure(expected, actual, e, testClassName, testMethodName, caseName);
            } catch (Exception dumpException) {
                // ダンプ失敗はコンソールに警告を出すのみ
                System.err.println("WARNING: Failed to dump JSON comparison failure: " + dumpException.getMessage());
                dumpException.printStackTrace();
            }

            // 元のAssertionErrorを再スロー
            throw e;
        }
    }

    /**
     * JSON比較失敗時のファイルダンプを実行
     */
    private static void dumpJsonComparisonFailure(
            String expected,
            String actual,
            AssertionError error,
            String testClassName,
            String testMethodName,
            String caseName
    ) throws IOException {
        // ダンプディレクトリの作成
        Path testClassDir = DUMP_ROOT_DIR.resolve(testClassName);
        Files.createDirectories(testClassDir);

        // ファイル名のベース
        String baseFileName = String.format("%s_%s", testMethodName, caseName);

        // 1. 期待値JSONをダンプ
        Path expectedPath = testClassDir.resolve(baseFileName + "_expected.json");
        Files.writeString(expectedPath, expected, StandardCharsets.UTF_8);

        // 2. 実際の値JSONをダンプ
        Path actualPath = testClassDir.resolve(baseFileName + "_actual.json");
        Files.writeString(actualPath, actual, StandardCharsets.UTF_8);

        // 3. 差分情報（AssertionErrorメッセージ）をダンプ
        Path diffPath = testClassDir.resolve(baseFileName + "_diff.txt");
        String diffContent = formatDiffContent(error, expectedPath, actualPath);
        Files.writeString(diffPath, diffContent, StandardCharsets.UTF_8);

        // コンソールに出力パスを表示
        System.err.println("\n========================================");
        System.err.println("JSON Assertion Failed - Files dumped:");
        System.err.println("  Expected: " + expectedPath.toAbsolutePath());
        System.err.println("  Actual:   " + actualPath.toAbsolutePath());
        System.err.println("  Diff:     " + diffPath.toAbsolutePath());
        System.err.println("========================================\n");
    }

    /**
     * 差分ファイルの内容をフォーマット
     */
    private static String formatDiffContent(AssertionError error, Path expectedPath, Path actualPath) {
        StringBuilder sb = new StringBuilder();
        sb.append("JSON Assertion Error\n");
        sb.append("====================\n\n");
        sb.append("Expected File: ").append(expectedPath.toAbsolutePath()).append("\n");
        sb.append("Actual File:   ").append(actualPath.toAbsolutePath()).append("\n\n");
        sb.append("Error Message:\n");
        sb.append("====================\n");
        sb.append(error.getMessage()).append("\n\n");
        sb.append("Stack Trace:\n");
        sb.append("====================\n");
        for (StackTraceElement element : error.getStackTrace()) {
            sb.append("  at ").append(element.toString()).append("\n");
        }
        return sb.toString();
    }
}
