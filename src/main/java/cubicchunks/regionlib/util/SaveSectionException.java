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
