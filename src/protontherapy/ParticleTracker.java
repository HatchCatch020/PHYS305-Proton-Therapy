package protontherapy;

// Contains Euler and Runge-Kutte 4th order integrator methods

public class ParticleTracker 
{
    private double dt;
    private int steps;
    
    // changed from private
    public Particle input;
    public Particle output;
    
    private Track storeTrack;
    private Track findStepSize;
    private boolean useRK4;

    static final double c = 3e8; // speed of light in m/s

    public ParticleTracker(Particle particle, double Tmax, int N, boolean useRungeKutta4)
    {
        input = particle;
        steps = N;    // do one more step to arrive at final position
        dt = Tmax/N;    // deltaT to arrive at final time
        useRK4 = useRungeKutta4;
      

        // store the particle track in a separate object and save first point
        storeTrack = new Track(input.mass(), steps+1);
        storeTrack.savePositionMomentum(input);
        
    }

    public Pair track(Geometry Experiment, int ke)
    {
        // make two copies of the input particle:
        // output will evolve to the final particle, lastStep will keep the state before the last step
        output = new Particle(input);
        Particle lastStep = new Particle(output);
        
        double [] EnergyLossArray = new double [steps];
        double [] distance = new double [steps];
        double [] stepsize;
        
        int lastVolume = Experiment.getVolume(output);

        for(int n = 0; n < steps; n++){
            
            
            // propagate particle in steps
            if (useRK4) {
                propogateParticleRK4(dt); // Runge-Kutta 4th order integrator
            } else {
                propogateParticleEuler(dt);  // Euler integrator
            }

            // check, if the last step crossed into or over a different experimental volume
            double reduceStep = Experiment.scanVolumeChange(output, lastStep, lastVolume);
            if (reduceStep < 1.0) {
                // yes, we crossed a boundary: go back and move forward with a reduced step size
                output.setState(lastStep);
                if (useRK4) {
                    propogateParticleRK4(dt*reduceStep);
                } else {
                    propogateParticleEuler(dt*reduceStep);
                }
            }
            
            // implement Multiple scattering, uncomment to use
            Experiment.doMultScatter(output, output.distance(lastStep));
                
            // implement Energy Loss, uncomment to use
            if (output.E() > output.mass()) {
                // stores energy loss at each step in array (returns dE)
                EnergyLossArray[n] = Experiment.doEloss(output, output.distance(lastStep), ke);
                distance[n] = output.distance(lastStep);

            }
            
            // store the current position/mometum
            storeTrack.savePositionMomentum(output);

            // Abort if particle stopped moving
            //System.out.println(output.momentum());
            if (output.momentum() <= 0.) {
                //System.out.println("Particle out of energy, done.");
                break;
            }

            lastVolume = Experiment.getVolume(output);

            // !large debug output!
            // this should be commented in "production runs"

//             System.out.println("After step " + n);
//             output.print();
//             System.out.println("In volume " + lastVolume);

            // end of debug output

            if (!Experiment.isInVolume(output, 0)) {
                // System.out.println("After " + n + " steps the particle left the world, done.");
                // output.print();
                break;
            }

            // save last state
            lastStep.setState(output);
            
            // get step array
            //Track track = new Track(input.mass(), steps+1);
            //stepsize[n] = storeTrack.findStepSize(n);
    
        }

        // return the final, propagated particle ***
        return new Pair(output, EnergyLossArray, distance);
    }

    public Track getTrack()
    {
        return storeTrack;
    }
    
    public double [] Bfield(double t, double x, double y, double z)
    {
        // define vectorial B-field in Tesla at position t, x, y, z

        // Example: zero B field
        double [] B = {0., 0., 0.};

        return B;
    }

    public double [] Efield(double t, double x, double y, double z)
    {
        // define vectorial E-field in V/m at position t, x, y, z

        // Example: zero E field
        double [] E = {0., 0., 0.};

        return E;
    }
    
    public void propogateParticleEuler(double mydt)
    {
         // get state of particle as array of 8 elements: (t,x,y,z,E,px,py,pz)
        double [] stateIni = output.getState();

        // this is the relativistic speed, zeroth component is 1 (time)
        // then follow three components of the relativistic speed v=c*p/E
        double[] v = {1, c*stateIni[5]/stateIni[4],
                      c*stateIni[6]/stateIni[4], c*stateIni[7]/stateIni[4]};

        // this calculates the vectorial Lorentz force E + (v x B)
        // at the position given by stateIni
        double[] F = LorentzForce(output.Q, v, stateIni);

        // make an Euler step and store result
        double[] stateFinal = new double[8];
        for(int i = 0; i < 4; i++){
            // move in time/space by mydt*v
            stateFinal[i] = stateIni[i] + mydt*v[i];
            // update the momentum according to Lorentz force
            stateFinal[i+4] = stateIni[i+4] + mydt*F[i];
        }
        // store the new position and momentum
        output.setState(stateFinal);
    }

    public void propogateParticleRK4(double mydt)
    {
        // get state of particle as array of 8 elements: (t,x,y,z,E,px,py,pz)
        double [] stateIni = output.getState();

        // "magic" Runge-Kutta 4th order constants
        final double[] a = {0, 0.5, 0.5, 1.};
        final double[] b = {1./6., 1./3., 1./3., 1./6.};
        
        double[] stateFinal = new double[8];
        double[] stateIntermed = new double[8];

        // speed and force, initialise arrays with zeros to prepare for the first step
        double[] v = {0., 0., 0., 0.};
        double[] F = {0., 0., 0., 0.};

        for (int step = 0; step < 4; step++) {
            for(int i = 0; i < 4; i++){
                // move in space/time by a*mydt*v
                stateIntermed[i] = stateIni[i] + a[step]*mydt*v[i];
                // update the momentum according to Lorentz force
                stateIntermed[i+4] = stateIni[i+4] + a[step]*dt*F[i];
            }
        
            // this is the relativistic speed v=c*p/E
            v[0] = 1.;
            v[1] = c*stateIntermed[5]/stateIntermed[4];
            v[2] = c*stateIntermed[6]/stateIntermed[4];
            v[3] = c*stateIntermed[7]/stateIntermed[4];

            // this calculates the vectorial Lorentz force E + (v x B)
            // at the position given by stateIntermed
            F = LorentzForce(output.Q, v, stateIntermed);

            for(int i = 0; i < 4; i++) {
                // move in time/space by b*mydt*v
                stateFinal[i] = stateFinal[i] + b[step]*mydt*v[i];
                // update the momentum according to Lorentz force
                stateFinal[i+4] = stateFinal[i+4] + b[step]*mydt*F[i];
            }
        }
        
        // add changes to initial state and store
        for(int i = 0; i < 8; i++){
            stateFinal[i] = stateFinal[i] + stateIni[i];
        }
        output.setState(stateFinal);
    }

    public double [] LorentzForce(double Q, double [] v, double [] Y)
    {
        // get the E and B fields at coordinates t, x, y, z
        double[] E = Efield(Y[0], Y[1], Y[2], Y[3]);
        double[] B = Bfield(Y[0], Y[1], Y[2], Y[3]);
        
        // this is the vectorial Lorentz force: q*c(E + (v x B))
        // zeroth component is gain in energy
        // note x-component of v -> v[1], but x-components of E,B -> E[0], B[0]
        double [] LF = {Q*1E-6*(E[0]*v[1]+E[1]*v[2]+E[2]*v[3]),
                        Q*c*1E-6*(E[0] + v[2]*B[2]-v[3]*B[1]),
                        Q*c*1E-6*(E[1] + v[3]*B[0]-v[1]*B[2]),
                        Q*c*1E-6*(E[2] + v[1]*B[1]-v[2]*B[0])};
        return LF;
    }

}