package protontherapy;

import java.io.*;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.Math;
import java.util.Random;
import static protontherapy.ProtonTherapy.randGen;

class Geometry
{
    // Class to define the Geometry of an experiment
    // the "type" is a number defining the basic geometric object
    // the "rho_Z_A" array stores material information: 
    //    density, atomic number (nucleus charge), mass number (protons+neutrons)
    // the "shape" array stores the geometrical information
    // * cuboid: type=1,
    //   shape = 6 values representing two corners of the internal diagonal (x0,y0,z0), (x1,y1,z1)
    // every basic object can be identified by a number
    // the first object is taken as to be the "world",
    //    i.e. we can use it to abort the simulation
    // maxShapes is the maximum allowed number of basic objects, extend if needed

    // the class also impelments thee additional helper functions:
    // * doEloss -- calculate EnergyLoss via associated class and apply to Particle
    // * doMultScatter -- calculate MultipleScattering via associated class and apply to Particle
    // * detectParticle -- simulate a detected particle position
    
    private Random randGen;
    private static final int maxShapes = 100;
    private int nshapes;
    private int [] type;
    private double [] rho;
    private double [] Z;
    private double [] A;
    private String [] names;
    
    private double BivGaussSigma = 6;
    private double BivGaussScale = 60;

    private double [][] shapes;
    
    private EnergyLoss [] Eloss;
    private MCS [] MultScatter;
    
    private Voxel voxels;

    private double minfeaturesize;
    
    int numScatter;
    
    int lastpos;

    public Geometry(double featuresize)
    {
        randGen = new Random();
        nshapes = 0;
        type = new int[maxShapes];
        rho = new double[maxShapes];
        Z = new double[maxShapes];
        A = new double[maxShapes];
        shapes = new double[maxShapes][];
        names = new String[maxShapes];
        
        Eloss = new EnergyLoss[maxShapes];
        MultScatter = new MCS[maxShapes];

        minfeaturesize = featuresize;
        
        // Got to fix this error
        
        double [] binlow = {-0.2, -0.2, 0.22};
        double [] binhigh = {0.2, 0.2, 0.72};
        
//        Set last parameter to true to output individual bragg peaks        
        voxels = new Voxel(100, binlow, binhigh, "Z Slices", true);
        
    }

    public int getNshapes() { return nshapes; }
    
    public double bivarGaussian(double x, double y) {
        double zGauss = (1/(2*Math.PI*BivGaussSigma*BivGaussSigma))
                *Math.exp(-0.5*((Math.pow(x*BivGaussScale, 2)/Math.pow(BivGaussSigma,2))+(Math.pow(y*BivGaussScale, 2)/Math.pow(BivGaussSigma, 2))));
        //System.out.println(zGauss);
        return zGauss*5;
    }
   

    public int AddCuboid(double[] pos,
                         double rhoin, double Zin, double Ain,
                         String name)
    {
        if (nshapes >= maxShapes) {
            return -1;
        }
        
        type[nshapes] = 1;
        shapes[nshapes] = new double[6];
        shapes[nshapes][0] = pos[0];    // x0
        shapes[nshapes][1] = pos[1];    // y0    
        shapes[nshapes][2] = pos[2];    // z0
        shapes[nshapes][3] = pos[3];    // x1
        shapes[nshapes][4] = pos[4];    // y1
        shapes[nshapes][5] = pos[5];    // z1

        rho[nshapes] = rhoin;
        Z[nshapes] = Zin;
        A[nshapes] = Ain;
        names[nshapes] = name;

        
        Eloss[nshapes] = new EnergyLoss(rhoin, Zin, Ain);
        MultScatter[nshapes] = new MCS(rhoin, Zin, Ain);

        nshapes++;
        return (nshapes-1);
    }
    
    public int AddContour(double x0, double y0, double z0,
                          double rhoin, double Zin, double Ain,
                          String name){
        
        if (nshapes >= maxShapes){
            return -1;
        }
        
        type[nshapes] = 2;
        shapes[nshapes] = new double[6];
        shapes[nshapes][0] = x0;
        shapes[nshapes][1] = y0;
        shapes[nshapes][2] = z0;
        shapes[nshapes][3] = 0;
        shapes[nshapes][4] = 0;
        shapes[nshapes][5] = 0;
        
        rho[nshapes] = rhoin;
        Z[nshapes] = Zin;
        A[nshapes] = Ain;
        names[nshapes] = name;
        
        Eloss[nshapes] = new EnergyLoss(rhoin, Zin, Ain);
        MultScatter[nshapes] = new MCS(rhoin, Zin, Ain);
        
        nshapes++;
        return (nshapes-1);
    }
    
    public int AddAperture(double apertureRad, double outerRad,
                           double z0, double z1,
                           double rhoin, double Zin, double Ain,
                          String name){
        if (nshapes >= maxShapes){
            return -1;
        }
        
        type[nshapes] = 3;
        shapes[nshapes] = new double[4];
        shapes[nshapes][0] = apertureRad;
        shapes[nshapes][1] = outerRad;
        shapes[nshapes][2] = z0;
        shapes[nshapes][3] = z1;

        
        rho[nshapes] = rhoin;
        Z[nshapes] = Zin;
        A[nshapes] = Ain;
        names[nshapes] = name;
        
        Eloss[nshapes] = new EnergyLoss(rhoin, Zin, Ain);
        MultScatter[nshapes] = new MCS(rhoin, Zin, Ain);
        
        nshapes++;
        
    return (nshapes-1);    
    }
    
    public void Print()
    {
        System.out.println("stored " + getNshapes() + " objects.");
        for (int i = 0; i < nshapes; i++) {
            if (i == 0) {
                System.out.println("Maximum size of experiment given by object 0:");
            }
            if (type[i] == 1) {
                System.out.println("Geometry object #" + i + " = cuboid. ("+names[i]+".)");
                System.out.printf("   corners (%f, %f, %f) - (%f, %f, %f)%n",
                                  shapes[i][0], shapes[i][1], shapes[i][2],
                                  shapes[i][3], shapes[i][4], shapes[i][5]);
            }
            if (type[i] == 2) {
                System.out.println("Geometry object #" + i + " = contoured material.("+names[i]+".)");
                System.out.printf("   corners (%f, %f, %f) - (%f, %f, %f)%n",
                                  shapes[i][0], shapes[i][1], shapes[i][2],
                                  shapes[i][3], shapes[i][4], shapes[i][5]);
            }
             if (type[i] == 3) {
                System.out.println("Geometry object #" + i + " = Aperture.("+names[i]+".)");
                System.out.printf("   corners (%f, %f, %f) ",
                                  shapes[i][0], shapes[i][1], shapes[i][2]);
            }
            System.out.printf("   material rho = %.3f g/cm^3, Z = %f, A = %f%n",
                              rho[i], Z[i], A[i]);
        }
        System.out.println("When scanning for volume transitions, the smallest feature size discovered will be " + minfeaturesize + "m.");
    }

    public boolean isInVolume(double x, double y, double z, int id)
    {
        // test if point (x,y,z) is in volume with identifier id
        
        // abort if being asked for an undefined volume
        if (id >= getNshapes()) {
            return false;
        }
        
        switch(type[id]){
            case 1:
            // cuboid
            return ( shapes[id][0] <= x
                     && shapes[id][1] <= y
                     && shapes[id][2] <= z
                     && x <= shapes[id][3]
                     && y <= shapes[id][4]
                     && z <= shapes[id][5] );
            case 2:
            // contoured scatter
                //System.out.println(bivarGaussian(x, y)+shapes[id][2]);
            return ( // base 
                     shapes[id][2] <= z
                    // bivariate Gaussian
                     && z <= ((bivarGaussian(x, y))+shapes[id][2]));
            
            case 3:
            // 3D annulus
                double particleRad = Math.sqrt(Math.pow(x, 2)+Math.pow(y, 2));
                //System.out.println(particleRad);
                // checking above inner circle
            return ( shapes[id][0] <= particleRad
                    && shapes[id][2] <= z
                    
                    // checking below outer circle
                    && particleRad <= shapes[id][1] 
                    && z <= shapes[id][3] );
        }
       
        
        return false;
    }
    
    public int getVolume(double x, double y, double z)
    {
        // cycle through volumes in opposite order
        for (int i = getNshapes()-1; i >= 0; i--) {
            if (isInVolume(x, y, z, i)) {
//                System.out.println(i);
                return i;
            }
        }
        
        // if we arrived here, we are outside everything
        return -1;
    }

    public boolean isInVolume(Particle p, int id)
    {
        // test if particle p is currently in volume with identifier id
        return isInVolume(p.x, p.y, p.z, id);
    }
    
    public int getVolume(Particle p)
    {
        // get the highest volume number the particle is in
        return getVolume(p.x, p.y, p.z);
    }
    
    public void doEloss(Particle p, double dist, int beamWeight)
    {
        int volume = getVolume(p);

        if (volume >= 1) {
            double lostE = Eloss[volume].getEnergyLoss(p)*dist;
            p.reduceEnergy(lostE);
            //System.out.println(p.momentum());
            if(isInVolume(p, 2)){
                if(isInVolume(p, 4)){System.out.println("1");}
//               System.out.println("Dog");
//               System.out.println(lostE);
//                double stdev = 0.1;
//                double smearing = randGen.nextGaussian()*stdev;
                voxels.fill(lostE, p, beamWeight);

            }
        }
    }
 
    public void writeEnergyHist(double depth, String filename){
        //voxels.writeToDisk(filename);
        voxels.writeData(depth, filename);
    }
    
    public void doMultScatter(Particle p, double dist)
    {
        int volume = getVolume(p);
        
        // Changed from zero to one so if in vacuum not call MCS
        if (volume < 1) {
            return;
        }
        
        double theta0 = MultScatter[volume].getTheta0(p, dist);
        //System.out.println(theta0);

        if (Math.abs(theta0) > 0.) {
            p.applySmallRotation(randGen.nextGaussian()*theta0,
                                 randGen.nextGaussian()*theta0);
            
        }
        
    }

    public double [][] detectParticles(Track simtrack)
    {
        double [][] detection = new double[getNshapes()][4];

        // loop over each volume and average over the matching points
        for (int idet = 1; idet < getNshapes(); idet++) {

            // count points in volume
            int ncross = 0;

            for (int ipoint = 0; ipoint < simtrack.lastpos; ipoint++) {
                if (getVolume(simtrack.x[ipoint], simtrack.y[ipoint], simtrack.z[ipoint]) == idet) {
                    detection[idet][0] += simtrack.t[ipoint];
                    detection[idet][1] += simtrack.x[ipoint];
                    detection[idet][2] += simtrack.y[ipoint];
                    detection[idet][3] += simtrack.z[ipoint];
                    ncross++;
                }
            }
            if (ncross > 0) {
                for (int i = 0; i < 4; i++) {
                    detection[idet][i] /= ncross;
                }
            }
        }
        
        return detection;
    }

    public double scanVolumeChange(Particle p, Particle pold, int lastVolume)
    {
        // scan the straight line between last and new position, if we missed a
        // feature of the experiment
        double dist = p.distance(pold);
        int nsteps = (int) (dist/minfeaturesize) + 1;

        if (nsteps <= 1) {
            // step was small enough, continue
            return 1.;
        }
        
        double [] pos = {pold.x, pold.y, pold.z};
        double [] end = {p.x, p.y, p.z};
        double [] delta = new double[3];
        for (int i = 0; i < 3; i++) {
            delta[i] = (end[i]-pos[i])/nsteps;
        }
        for (int n = 1; n <= nsteps; n++) {
            if (getVolume(pos[0]+n*delta[0], pos[1]+n*delta[1], pos[2]+n*delta[2]) != lastVolume) {
                if (n == 1) {
                    return (0.5 / nsteps);
                } else {
                    return (n-1.0) / nsteps;
                }
            }
        }
        return 1.;
    }
}