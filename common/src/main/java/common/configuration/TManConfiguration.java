package common.configuration;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.Properties;

public final class TManConfiguration {

    private final long period;
    private final long seed;
    private final double temperature;
    
    //TODO: Gradient Change.
    private int similarViewSize;
    private int shuffleLength;
    private long shuffleTimeout;


    public TManConfiguration(long seed, long period, double temperature) {
        super();
        this.seed = seed;
        this.period = period;
        this.temperature = temperature;
    }

    public TManConfiguration(long seed, long period, double temperature , int similarViewSize , int shuffleLength, long shuffleTimeout) {
        this(seed,period , temperature);
        this.similarViewSize = similarViewSize;
        this.shuffleLength = shuffleLength;
        this.shuffleTimeout = shuffleTimeout;
    }
    
    public long getSeed() {
        return seed;
    }


    public int getSimilarViewSize(){
        return this.similarViewSize;
    }
    
    public long getPeriod() {
        return this.period;
    }

    public int getShuffleLength(){
        return this.shuffleLength;
    }
    
    public long getShuffleTimeout(){
        return this.shuffleTimeout;
    }
    
    public double getTemperature() {
        return temperature;
    }
    

    public void store(String file) throws IOException {
        Properties p = new Properties();
        p.setProperty("seed", "" + seed);
        p.setProperty("period", "" + period);
        p.setProperty("temperature", "" + temperature);
        p.setProperty("similarViewSize",""+ similarViewSize);
        p.setProperty("shuffleLength",""+ shuffleLength);
        p.setProperty("shuffleTimeout","" + shuffleTimeout);

        Writer writer = new FileWriter(file);
        p.store(writer, "se.sics.kompics.p2p.overlay.application");
    }


    public static TManConfiguration load(String file) throws IOException {
        Properties p = new Properties();
        Reader reader = new FileReader(file);
        p.load(reader);

        long seed = Long.parseLong(p.getProperty("seed"));
        long period = Long.parseLong(p.getProperty("period"));
        double temp = Double.parseDouble(p.getProperty("temperature"));
        
        //FIXME: Fix the case of the new T-Man Configuration.
        //TODO: Gradient Change.
        int similarViewSize = Integer.parseInt(p.getProperty("similarViewSize"));
        int shuffleLength = Integer.parseInt(p.getProperty("shuffleLength"));
        long shuffleTimeout  = Long.parseLong(p.getProperty("shuffleTimeout"));
        
        return new TManConfiguration(seed, period, temp,similarViewSize, shuffleLength,shuffleTimeout);
    }
}
