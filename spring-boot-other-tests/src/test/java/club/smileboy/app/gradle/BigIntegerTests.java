package club.smileboy.app.gradle;

import com.sun.org.apache.xpath.internal.operations.String;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.Arrays;

/**
 * @author FLJ
 * @date 2022/6/9
 * @time 11:27
 * @Description gradle wrapper task hash caculation...
 *
 *
 * 这里主要是探讨  grale wrapper 任务在将对应的distribution 包解压并创建一个存放的根目录所用的hash算法的一部分 ..
 * 它将 发行包名称使用MD5 加密之后 -> 作为一个大数处理 ..
 * 然后将这个大数 作为 BigInteger 的magnitude 片段,然后进行大数合成 ... 最终实现 36进制下产生一个文件夹(具有 0-9 a-z) ...
 *
 *
 * 随便再将进制  符号位进行理解 ....
 *
 * 参考文章: http://www.hollischuang.com/archives/176
 *
 *
 */
public class BigIntegerTests {

	public static void main(String[] args) throws Exception {
		MessageDigest md5 = MessageDigest.getInstance("MD5");
		md5.update("https\\://services.gradle.org/distributions/".getBytes());
		byte[] digest = md5.digest();
		System.out.println(new String());
		System.out.println(Arrays.toString(digest));
		System.out.println(new BigInteger(1, digest));


		// 将大数的符号级表示转为 大数  ..
		byte[] values = new byte[] {0x10};
		System.out.println(new BigInteger(1,values));
	}


	/**
	 * 学习一下  进制符号
	 *
	 * 主要是看到了{@link BigInteger#BigInteger(int, int[])}  int[] 是一个大端序列( 数据在尾端 ...,有效数据在低位) ..
	 */
	@Test
	public void test() {
		System.out.println(0x4000000000000000L);
		System.out.println(0x4); // 16进制
		System.out.println(0x16); // 16进制  00010110 => 16 + 4 + 2
		System.out.println(0x26);
		Integer integer = Integer.valueOf("22", 10);
		System.out.println(BigInteger.valueOf(integer).toString(16));

		System.out.println(1 << 16);

		//		整数 4个字节 ... 32位,表示最大有符号整数  2^31
		System.out.println(((long) Math.pow(2, 31)));
		System.out.println((long) Math.pow(2,32)); // 最大无符号 ..
		// 所以 1 向左移 31位 - 1 最大有符号整数 ...
		int value  = 1 <<  31 -1;
		System.out.println(value);


		// 于是 3进制的最大位数应该是,选19位超过 int最大值大小 ...
		//		3^x <=  2147483647 < 3^y
		System.out.println(((long) Math.pow(3, 19)));
		System.out.println((long)Math.pow(3,20));

		System.out.println(BigInteger.valueOf(1162261467).toString(16));

		// 可以看到 2进制的最佳数 => 2 ^30 次方,这样拿出来的一个值 永远是小于等于它的范围,不会溢出 ...
		System.out.println(new BigInteger("40000000",16).toString());


		
		
	}
}
