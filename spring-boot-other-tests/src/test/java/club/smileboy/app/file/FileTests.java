package club.smileboy.app.file;

import org.junit.jupiter.api.Test;

import java.io.File;

public class FileTests {
	@Test
	public void test() {
		// 这是文件分隔符
		System.out.println(File.separator);
		// 路径(路径与路径之间的分隔符) ...
		System.out.println(File.pathSeparator);
	}
}
