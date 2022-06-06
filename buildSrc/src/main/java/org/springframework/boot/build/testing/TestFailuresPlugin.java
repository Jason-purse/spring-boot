/*
 * Copyright 2012-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.boot.build.testing;

import java.util.ArrayList;
import java.util.List;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.testing.Test;
import org.gradle.api.tasks.testing.TestDescriptor;
import org.gradle.api.tasks.testing.TestListener;
import org.gradle.api.tasks.testing.TestResult;

/**
 * 这是一个记录测试失败并在构建结束之后报告它们的一个插件 ...
 *
 * Plugin for recording test failures and reporting them at the end of the build.
 *
 * @author Andy Wilkinson
 */
public class TestFailuresPlugin implements Plugin<Project> {

	@Override
	public void apply(Project project) {

		// 这应该是一种设计模式
//		OSGI ... 注册方式 ...
		Provider<TestResultsOverview> testResultsOverview = project.getGradle().getSharedServices()
				.registerIfAbsent("testResultsOverview", TestResultsOverview.class, (spec) -> {
				});

		// 总体来说 就是给任务 增加了额外的功能,查看测试结果失败预览 ..
		project.getTasks().withType(Test.class,
				// 配置一个任务 ...
				// 详情
//				https://docs.gradle.org/current/dsl/org.gradle.api.tasks.testing.Test.html#N2F9B6
				(test) -> test.addTestListener(new FailureRecordingTestListener(testResultsOverview, test)));
	}

	// 增加一个测试监听器,一个私有监听器 这种属于什么设计模式?
	// 只提供抽象,而不给具体实现,如果用户需要 自己实现 ...
	private final class FailureRecordingTestListener implements TestListener {

		private final List<TestDescriptor> failures = new ArrayList<>();

		private final Provider<TestResultsOverview> testResultsOverview;

		/**
		 * 哪一个测试任务 ..
		 */
		private final Test test;

		private FailureRecordingTestListener(Provider<TestResultsOverview> testResultOverview, Test test) {
			this.testResultsOverview = testResultOverview;
			this.test = test;
		}

		@Override
		public void afterSuite(TestDescriptor descriptor, TestResult result) {
			// 如果失败不为空 ..
			if (!this.failures.isEmpty()) {
				// 实例化 构建服务,并增加失败信息 ...
				this.testResultsOverview.get().addFailures(this.test, this.failures);
			}
		}

		@Override
		public void afterTest(TestDescriptor descriptor, TestResult result) {
			// 测试之后,如果测试个数大于 0
			if (result.getFailedTestCount() > 0) {
				// 增加测试失败描述 ..
				this.failures.add(descriptor);
			}
		}

		@Override
		public void beforeSuite(TestDescriptor descriptor) {

		}

		@Override
		public void beforeTest(TestDescriptor descriptor) {

		}

	}

}
