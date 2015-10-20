/*
 * SonarQube Java
 * Copyright (C) 2012 SonarSource
 * sonarqube@googlegroups.com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonar.java;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import org.junit.Before;
import org.junit.Test;
import org.sonar.plugins.java.api.IssuableSubscriptionVisitor;
import org.sonar.plugins.java.api.JavaCheck;
import org.sonar.plugins.java.api.JavaFileScannerContext;
import org.sonar.plugins.java.api.tree.Tree;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.fest.assertions.Assertions.assertThat;

public class JavaVersionAwareVisitorTest {

  private JavaCheck[] javaChecks;
  private List<String> messages;

  @Before
  public void setUp() throws Exception {
    messages = Lists.newLinkedList();
    javaChecks = new JavaCheck[] {
      new JavaVersionCheck(7, messages),
      new JavaVersionCheck(8, messages),
      new SimpleCheck(messages),
      new ContextualCheck(messages)
    };
  }

  @Test
  public void all_check_executed_when_no_java_version() {
    checkIssues(new JavaConfiguration(Charsets.UTF_8));
    assertThat(messages).containsExactly("JavaVersionCheck_7", "JavaVersionCheck_8", "SimpleCheck", "ContextualCheck");
  }

  @Test
  public void all_check_executed_when_invalid_java_version() {
    JavaConfiguration conf = new JavaConfiguration(Charsets.UTF_8);
    conf.setJavaVersion(null);
    checkIssues(conf);
    assertThat(messages).containsExactly("JavaVersionCheck_7", "JavaVersionCheck_8", "SimpleCheck", "ContextualCheck");
  }

  @Test
  public void only_checks_with_adequate_java_version_higher_than_configuration_version_are_executed() {
    JavaConfiguration conf = new JavaConfiguration(Charsets.UTF_8);
    conf.setJavaVersion(7);
    checkIssues(conf);
    assertThat(messages).containsExactly("JavaVersionCheck_7", "SimpleCheck", "ContextualCheck_7");

    conf.setJavaVersion(8);
    checkIssues(conf);
    assertThat(messages).containsExactly("JavaVersionCheck_7", "JavaVersionCheck_8", "SimpleCheck", "ContextualCheck_8");
  }

  @Test
  public void no_java_version_matching() {
    JavaConfiguration conf = new JavaConfiguration(Charsets.UTF_8);
    conf.setJavaVersion(6);
    checkIssues(conf);
    assertThat(messages).containsExactly("SimpleCheck", "ContextualCheck_6");
  }

  private void checkIssues(JavaConfiguration conf) {
    messages.clear();
    ArrayList<File> files = Lists.newArrayList(new File("src/test/files/JavaVersionAwareChecks.java"));

    JavaSquid squid = new JavaSquid(conf, null, null, null, javaChecks);
    squid.scan(files, Collections.<File>emptyList(), Collections.<File>emptyList());
  }

  private static class SimpleCheck extends IssuableSubscriptionVisitor {
    private final List<String> messages;

    public SimpleCheck(List<String> messages) {
      this.messages = messages;
    }

    @Override
    public List<Tree.Kind> nodesToVisit() {
      return ImmutableList.of(Tree.Kind.CLASS);
    }

    @Override
    public void visitNode(Tree tree) {
      messages.add(getName());
    }

    public String getName() {
      return this.getClass().getSimpleName().toString();
    }
  }

  private static class ContextualCheck extends SimpleCheck {

    private Integer javaVersion;

    public ContextualCheck(List<String> messages) {
      super(messages);
    }

    @Override
    public void scanFile(JavaFileScannerContext context) {
      this.javaVersion = context.getJavaVersion();
      super.scanFile(context);
    }

    @Override
    public String getName() {
      return super.getName() + (javaVersion == null ? "" : "_" + javaVersion);
    }

  }

  private static class JavaVersionCheck extends SimpleCheck implements JavaVersionAwareVisitor {

    private final Integer target;

    private JavaVersionCheck(Integer target, List<String> messages) {
      super(messages);
      this.target = target;
    }

    @Override
    public boolean isCompatibleWithJavaVersion(Integer version) {
      return target <= version;
    }

    @Override
    public String getName() {
      return super.getName() + "_" + target;
    }
  }
}