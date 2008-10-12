/* $HeadURL::                                                                            $
 * $Id$
 *
 * Copyright (c) 2008 by Topaz, Inc.
 * http://topazproject.org
 *
 * Licensed under the Educational Community License version 1.0
 * http://opensource.org/licenses/ecl1.php
 */

package org.topazproject.ambra.article.util;

/**
 * ImageProcessingException.
 *
 * @author jkirton
 */
public class ImageProcessingException extends Exception {
  /**
   * Create a new exception with the given cause.
   *
   * @param cause the underlying exception causing this one
   */
  public ImageProcessingException(Throwable cause) {
    super(cause);
  }

  /**
   * Create a new exception with the given message.
   *
   * @param message the exception message
   */
  public ImageProcessingException(String message) {
    super(message);
  }

  /**
   * Create a new exception with the given message and cause.
   *
   * @param message the exception message
   * @param cause the underlying exception causing this one
   */
  public ImageProcessingException(String message, Throwable cause) {
    super(message, cause);
  }
}
