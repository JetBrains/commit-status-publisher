/*
 * Copyright 2000-2022 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jetbrains.buildServer.commitPublisher;

import jetbrains.buildServer.serverSide.InvalidProperty;
import jetbrains.buildServer.serverSide.PropertiesProcessor;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Collection;

import static jetbrains.buildServer.util.Util.map;
import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.groups.Tuple.tuple;

@Test
public class CommitStatusPublisherFeatureTest extends CommitStatusPublisherTestBase {

  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
  }

  public void should_not_allow_to_save_feature_without_publisher_selected() {
    PropertiesProcessor processor = myFeature.getParametersProcessor(myBuildType);
    Collection<InvalidProperty> errors = processor.process(map(Constants.VCS_ROOT_ID_PARAM, "someRootId"));
    then(errors).extracting("propertyName", "invalidReason").contains(tuple(Constants.PUBLISHER_ID_PARAM, "Choose a publisher"));

    errors = processor.process(map(Constants.VCS_ROOT_ID_PARAM, "someRootId", Constants.PUBLISHER_ID_PARAM, DummyPublisherSettings.ID));
    then(errors).extracting("propertyName", "invalidReason").contains(tuple(Constants.PUBLISHER_ID_PARAM, "Choose a publisher"));
  }
}
