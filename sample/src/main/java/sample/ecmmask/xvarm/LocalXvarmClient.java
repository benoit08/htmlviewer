package sample.ecmmask.xvarm;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 엔진 없이 동작하는 XVARM 대체 구현 (edms 의 NoXVarmAPI 와 같은 역할).
 * 요소 하나 = 폴더의 파일 하나. shortId 는 실제 엔진처럼 등록 시각 기반 16자리.
 */
public class LocalXvarmClient implements XvarmClient {

    private final File dir;
    private int seq = 0;

    public LocalXvarmClient(String localDir) {
        this.dir = new File(localDir).getAbsoluteFile();
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("폴더를 만들 수 없습니다: " + dir);
        }
    }

    public synchronized String create(byte[] content) {
        String stamp = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
        DecimalFormat two = new DecimalFormat("00");
        String id;
        do {
            id = stamp + two.format(seq++ % 100);
        } while (new File(dir, id).exists());

        OutputStream out = null;
        try {
            out = new FileOutputStream(new File(dir, id));
            out.write(content);
        } catch (IOException e) {
            throw new XvarmException("create 실패", e);
        } finally {
            closeQuietly(out);
        }
        return id;
    }

    public byte[] get(String shortId) {
        File f = file(shortId);
        if (!f.exists()) {
            throw new XvarmException(1, "요소 없음: " + shortId);
        }
        InputStream in = null;
        try {
            in = new FileInputStream(f);
            ByteArrayOutputStream bos = new ByteArrayOutputStream((int) f.length());
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                bos.write(buf, 0, n);
            }
            return bos.toByteArray();
        } catch (IOException e) {
            throw new XvarmException("getContent 실패", e);
        } finally {
            closeQuietly(in);
        }
    }

    public void delete(String shortId) {
        File f = file(shortId);
        if (f.exists() && !f.delete()) {
            throw new XvarmException(1, "delete 실패: " + shortId);
        }
    }

    private File file(String shortId) {
        if (shortId == null || !shortId.matches("\\d{16}")) {
            throw new XvarmException(5, "잘못된 ElementID: " + shortId);
        }
        return new File(dir, shortId);
    }

    private static void closeQuietly(java.io.Closeable c) {
        if (c != null) {
            try {
                c.close();
            } catch (IOException ignore) {
                // 무시
            }
        }
    }
}
