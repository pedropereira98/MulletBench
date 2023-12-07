package pt.haslab.mulletbench;

public class TimeProvider {
    private final static long  nanoTimeDiff;
    static {
        nanoTimeDiff = System.currentTimeMillis()*1_000_000-System.nanoTime();
    }

    public static long getNanoTime(){
        return System.nanoTime()+nanoTimeDiff;
    }
}
