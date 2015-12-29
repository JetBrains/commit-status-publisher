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
