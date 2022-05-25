package club.smileboy.app;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * @author FLJ
 * @date 2022/5/25
 * @time 16:08
 * @description URL test
 */
public class URLTests {
	public static void main(String[] args) throws MalformedURLException {

		URL url = new URL("https://www.baidu.com/a");
		System.out.println(url.getPath());

	}
}
