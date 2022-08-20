package club.smileboy.app.classLoader;

import org.junit.jupiter.api.Test;

/**
 * @author FLJ
 * @date 2022/6/10
 * @time 15:53
 * @Description 类路径上的资源测试, 1.8版本(jdk)
 */
public class ClassResourceTests {


	@Test
	public void fromRootLoadByTest() {
		// 应该是存在的 ...
		System.out.println(ClassLoader.getSystemClassLoader().getResource("rs.txt"));

		// 应该也是存在的 ...
		// 由于Class.getResource 进行了路径的转换,所以一般位于类附近的资源 可以直接使用这种方式,但是也可以根据 / 进行  绝对路径查找 ...
		System.out.println(ClassResourceTests.class.getResource("/rs.txt"));


		// 但是这一种应该是不存在的 ...
		//
		System.out.println(ClassLoader.getSystemClassLoader().getResource("/rs.txt"));
	}

}
