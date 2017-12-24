/*
 *  This file is part of RegionLib, licensed under the MIT License (MIT).
 *
 *  Copyright (c) 2016 contributors
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *  THE SOFTWARE.
 */
package cubicchunks.regionlib.util;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Collection;
import java.util.Collections;

public class SaveSectionException extends IOException {

    private Collection<? extends Throwable> causes;

    public SaveSectionException(String description, Collection<? extends Throwable> causes) {
        super(description);
        this.causes = causes;
    }

    public Collection<Throwable> getCauses() {
        return Collections.unmodifiableCollection(causes);
    }

    @Override public void printStackTrace(PrintStream s) {
        super.printStackTrace(s);

        if (this.causes != null && !this.causes.isEmpty()) {
            s.println("Caused by:");
            int i = 1;
            for (Throwable t : causes) {
                s.println("Cause 1/" + causes.size());
                t.printStackTrace(s);
            }
        }
    }


}
