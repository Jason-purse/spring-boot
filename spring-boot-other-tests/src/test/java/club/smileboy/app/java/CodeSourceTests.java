package club.smileboy.app.java;

import club.smileboy.app.ClassLoaderTests;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.CodeSource;
import java.security.ProtectionDomain;

/**
 * @author FLJ
 * @date 2022/6/9
 * @time 17:37
 * @Description Code Source 测试....
 */
public class CodeSourceTests {

	@Test
	public void test() throws URISyntaxException, IOException {

		ProtectionDomain protectionDomain = ClassLoaderTests.class.getProtectionDomain();
		CodeSource codeSource = protectionDomain.getCodeSource();
		if(codeSource != null) {
			// 所以一般来说,通过它能够拿到它的类路径 ....
			URL location = codeSource.getLocation();
			System.out.println(location);

			if(location.getProtocol().equals("file")) {
				// file
				File file = new File(location.toURI());
				Files.walkFileTree(file.toPath(), new SimpleFileVisitor<Path>() {
					@Override
					public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
						System.out.println(file.toString());
						return FileVisitResult.CONTINUE;
					}
				});
			}
		}
	}
}
