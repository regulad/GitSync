package gitsync.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class OutputReader {
    private final InputStream inputStream;

    public OutputReader(InputStream inputStream) {
        this.inputStream = inputStream;
    }

    public List<String> getOutput() {
        List<String> output = new ArrayList<>();

        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(this.inputStream));

            String line;
            try {
                while ((line = br.readLine()) != null) {
                    output.add(line);
                }
            } catch (Throwable var6) {
                try {
                    br.close();
                } catch (Throwable var5) {
                    var6.addSuppressed(var5);
                }

                throw var6;
            }

            br.close();
        } catch (IOException var7) {
            var7.printStackTrace();
        }

        return output;
    }
}
