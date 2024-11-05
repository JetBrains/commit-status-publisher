

/*
 * Copyright 2000-2024 JetBrains s.r.o.
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

import org.testng.annotations.Test;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import static org.testng.AssertJUnit.fail;

@SuppressWarnings("Convert2Diamond")
@Test
public class ConstantsTest {

  //need that in order to mark non-default values in UI
  public void constants_should_be_unique() throws IllegalAccessException {
    Class<Constants> klass = Constants.class;
    Map<String, Field> values = new HashMap<String, Field>();
    for (Field f : klass.getFields()) {
      Object value = f.get(klass);
      if (!(value instanceof String))
        continue;
      String val = (String) value;
      Field sameValueField = values.get(val);
      if (sameValueField != null) {
        fail("Constants " + f.getName() + " and " + sameValueField.getName() + " have same value " + val);
      } else {
        values.put(val, f);
      }
    }
  }

}