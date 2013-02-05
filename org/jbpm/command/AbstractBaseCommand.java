package org.jbpm.command;


/**
 * Base class for all internal commands. Implements some 
 * convenience methods for {@link Command}s and generic toString
 * method.
 * 
 * @author bernd.ruecker@camunda.com
 */
public abstract class AbstractBaseCommand implements Command
{
  public String toString() {
    return this.getClass().getName() 
      + " ["
      + getAdditionalToStringInformation()
      + "]";
  }  

  public String getAdditionalToStringInformation() {
    return "";
  }
}
