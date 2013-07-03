/*
   Copyright 2009-2013 Attila Szegedi

   Licensed under both the Apache License, Version 2.0 (the "Apache License")
   and the BSD License (the "BSD License"), with licensee being free to
   choose either of the two at their discretion.

   You may not use this file except in compliance with either the Apache
   License or the BSD License.

   If you choose to use this file in compliance with the Apache License, the
   following notice applies to you:

       You may obtain a copy of the Apache License at

           http://www.apache.org/licenses/LICENSE-2.0

       Unless required by applicable law or agreed to in writing, software
       distributed under the License is distributed on an "AS IS" BASIS,
       WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
       implied. See the License for the specific language governing
       permissions and limitations under the License.

   If you choose to use this file in compliance with the BSD License, the
   following notice applies to you:

       Redistribution and use in source and binary forms, with or without
       modification, are permitted provided that the following conditions are
       met:
       * Redistributions of source code must retain the above copyright
         notice, this list of conditions and the following disclaimer.
       * Redistributions in binary form must reproduce the above copyright
         notice, this list of conditions and the following disclaimer in the
         documentation and/or other materials provided with the distribution.
       * Neither the name of the copyright holder nor the names of
         contributors may be used to endorse or promote products derived from
         this software without specific prior written permission.

       THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
       IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
       TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
       PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL COPYRIGHT HOLDER
       BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
       CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
       SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
       BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
       WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
       OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
       ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package org.dynalang.dynalink.beans;

import java.lang.annotation.Annotation;
import java.lang.reflect.AccessibleObject;

/**
 * Utility class that determines if a method or constructor is caller sensitive. It actually encapsulates two different
 * strategies for determining caller sensitivity; a more robust one that works if Dynalink runs as code with access
 * to {@code sun.reflect} package, and an unprivileged one that is used when Dynalink doesn't have access to that
 * package. Note that even the unprivileged strategy is ordinarily robust, but it relies on the {@code toString} method
 * of the annotation. If an attacker were to use a different annotation to spoof the string representation of the
 * {@code CallerSensitive} annotation, they could designate their own methods as caller sensitive. This however does not
 * escalate privileges, only causes Dynalink to never cache method handles for such methods, so all it would do would
 * decrease the performance in linking such methods. In the opposite case when an attacker could trick Dynalink into not
 * recognizing genuine {@code CallerSensitive} annotations, Dynalink would treat caller sensitive methods as ordinary
 * methods, and would cache them bound to a zero-privilege delegate as the caller (just what Dynalink did before it
 * could handle caller-sensitive methods). That would practically render caller-sensitive methods exposed through
 * Dynalink unusable, but again, can not lead to any privilege escalations. Therefore, even the less robust unprivileged
 * strategy is safe; the worst thing a successful attack against it can achieve is slight reduction in Dynalink-exposed
 * functionality or performance.
 */
public class CallerSensitiveDetector {
    private static final String CALLER_SENSITIVE_CLASS_NAME = "sun.reflect.CallerSensitive";
    private static final DetectionStrategy DETECTION_STRATEGY = getDetectionStrategy();

    static boolean isCallerSensitive(AccessibleObject ao) {
        return DETECTION_STRATEGY.isCallerSensitive(ao);
    }

    private static DetectionStrategy getDetectionStrategy() {
        try {
            return new PrivilegedDetectionStrategy();
        } catch(Throwable t) {
            return new UnprivilegedDetectionStrategy();
        }
    }

    abstract static class DetectionStrategy {
        abstract boolean isCallerSensitive(AccessibleObject ao);
    }

    static class PrivilegedDetectionStrategy extends DetectionStrategy {
        private static final Class<? extends Annotation> CALLER_SENSITIVE_ANNOTATION_CLASS = getCallerSensitiveClass();

        @SuppressWarnings("unchecked")
        private static Class<? extends Annotation> getCallerSensitiveClass() {
            try {
                return (Class<? extends Annotation>)Class.forName(CALLER_SENSITIVE_CLASS_NAME);
            } catch(ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        boolean isCallerSensitive(AccessibleObject ao) {
            return ao.getAnnotation(CALLER_SENSITIVE_ANNOTATION_CLASS) != null;
        }
    }

    static class UnprivilegedDetectionStrategy extends DetectionStrategy {
        private static final String CALLER_SENSITIVE_ANNOTATION_STRING = "@" + CALLER_SENSITIVE_CLASS_NAME + "()";

        @Override
        boolean isCallerSensitive(AccessibleObject o) {
            for(Annotation a: o.getAnnotations()) {
                if(String.valueOf(a).equals(CALLER_SENSITIVE_ANNOTATION_STRING)) {
                    return true;
                }
            }
            return false;
        }
    }
}
