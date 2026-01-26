/*
** This software is governed by the CeCILL-C V2 license under French law
** and abiding by the rules of distribution of free software. You can
** use, modify and/or redistribute the software under the terms of the
** CeCILL-C license as circulated by CEA, CNRS and INRIA at the following
** URL: "http://www.cecill.info".
**
** As a counterpart to the access to the source code and rights to copy,
** modify and redistribute granted by the license, users are provided
** only with a limited warranty and the software's author, the holder of
** the economic rights, and the successive licensors have only limited
** liability.
**
** In this respect, the user's attention is drawn to the risks associated
** with loading, using, modifying and/or developing or reproducing the
** software by the user in light of its specific status of free software,
** that may mean that it is complicated to manipulate, and that also
** therefore means that it is reserved for developers and experienced
** professionals having in-depth computer knowledge. Users are therefore
** encouraged to load and test the software's suitability as regards
** their requirements in conditions enabling the security of their
** systems and/or data to be ensured and, more generally, to use and
** operate it in the same conditions as regards security.
**
** The fact that you are presently reading this means that you have had
** knowledge of the CeCILL-C license and that you accept its terms.
*/

package org.ica.cosmo;

import static java.lang.Math.pow;

/*
Optimization of the mass of an I-beam. The length of the beam is L = 1 m, and its W-section is defined by the following variables:
	- width b
	- height h
	- flange thickness s
	- web thickness a
 ___________
 |____  ____|
     |  |
     |  | h   
     |  |      
    >|  |< a
 ____|  |___
 |__________| s
      b
	
The beam is fixed horizontally at one end and free at the other end, where a vertical force F = 1000N is applied.

               | F
  /|___________v
  /| 
         L    

The beam material has the following characteristics:
	- Young's modulus E = 70 GPa
	- density rho = 2700 kg/m^3

The following constraints must be respected:
	- von Mises: sigmaVM < 60 MPa
	- deflection: f < fmax = 3 mm
	- width: 46 mm < b < 73 mm
	- height: 80 mm < h < 140 mm
	- flange thickness: 5.2 mm < s < 6.9 mm
	- web thickness: 3.8 mm < a < 4.7 mm

For those unfamiliar with mechanics:
	- second moment of area Iz = b(h+s)^3/12-(b-a)(h-s)^3/12
	- max von Mises sigmaVM = F L (h+s) / (2 Iz)
	- deflection f = F L^3 / (3 E Iz) 
and, of course 
	- mass m = ((h-s)a + b s) L rho
	
Custom stopping criterion:
I-beams cannot be constructed with greater than micrometer precision.
It is therefore pointless to continue optimization when improvements are less than one micrometer.
The custom criterion we have established is as follows:
“If 20 consecutive BB evaluations show no improvement greater than one micrometer on either variable,
then we must stop.”
*/

import jNomad.AllParameters;
import jNomad.ArrayOfDouble;
import jNomad.ArrayOfString;
import jNomad.BBInputType;
import jNomad.BBInputTypeList;
import jNomad.BBOutputType;
import jNomad.BBOutputTypeList;
import jNomad.CacheBase;
import jNomad.CallbackType;
import jNomad.Double;
import jNomad.Eval;
import jNomad.EvalPoint;
import jNomad.EvalPointVector;
import jNomad.EvalType;
import jNomad.Evaluator;
import jNomad.MainStep;
import jNomad.Point;
import jNomad.Step;
import jNomad.SWIGTYPE_p_StepCbFunc;

public class OptimIBeam {
	static {
		String nomadHome = System.getenv("NOMAD_HOME");
		if (nomadHome == null) {
			System.out.println("NOMAD_HOME environment variable not set...");
			System.out.println(" Using default location /opt/Nomad/");
			System.out.println(
					" if Nomad is installed elsewhere, " + "please set NOMAD_HOME environment variable accordingly");
			nomadHome = new String("/opt/Nomad/"); // default location
		}
		System.out.println("NOMAD_HOME=" + nomadHome);
		try {
			String libpath = new String(nomadHome + "/build/release/interfaces/jNomad/");
			String os = System.getProperty("os.name");
			if (os.startsWith("Mac"))
				System.load(libpath + "libjNomad.jnilib");
			else if (os.startsWith("Windows"))
				System.load(libpath + "Release/jNomad.dll");
			else if (os.startsWith("Linux"))
				System.load(libpath + "libjNomad.so");
		} catch (UnsatisfiedLinkError e) {
			System.err.println("Native code library failed to load.\n" + e);
			System.exit(1);
		}
	}

	public class My_Evaluator extends Evaluator {
		private final double L = 1; // m
		private final double E = 70E9; // Pa
		private final double F = 1000; // N
		private final double rho = 2700; // kg/m^3

		// custom stopping criterion variables
		/*
		  private int last_succes_index;
		  private double[] bestX = new double[4]
		  private double minGain = 1E-3; // the minimal gain for any of the variables to validate a better solution
		  private int maxBBeval = 20; // maximal number of BB evaluation allowed to find a better solution
		*/  

		public My_Evaluator(AllParameters p) {
			super(p.getEvalParams(), EvalType.BB);
			// custom stopping criterion variables
			/*
			for (int i=0 ; i<4 ; i++) {
				bestX[i] = java.lang.Double.MAX_VALUE;
			}
			*/
		}

		public boolean eval_x(EvalPoint x, Double h_max, boolean[] count_eval) {
			// x = [b, h, s, a]
			double b = x.get(0).todouble();
			double h = x.get(1).todouble();
			double s = x.get(2).todouble();
			double a = x.get(3).todouble();

			// mass = ((h-s)a + b s) L rho
			double mass = ((h - s) * a + b * s) * L * rho;
			String bbo = String.valueOf(mass);

			// second moment of area Iz = b(h+s)^3/12-(b-a)(h-s)^3/12
			double Iz = b * pow(h + s, 3) / 12 - (b - a) * pow(h - s, 3) / 12;

			// max von Mises sigma = F L (h+s) / (2 Iz) - 60 < 0
			double sigma = F * L * pow(h + s, 3) / (2 * Iz) - 60E6;
			bbo += " " + String.valueOf(sigma);

			// deflection f = F L^3 / (3 E Iz) - 3 < 0
			double defl = F * pow(L, 3) / (3 * E * Iz) - 3E-3;
			bbo += " " + String.valueOf(defl);

			x.setBBO(bbo);
			count_eval[0] = true;

			// custom stopping criterion variables
			/*
				int index = mads.get_stats().get_bb_eval() + 1;
				if (index - last_succes_index > maxBBeval) {
					Mads.force_quit(0);
				}
			*/
			return true;
		}

		// custom stopping criterion variables
		/*
		  public void update_success(Stats stats, Eval_Point x) {
		  	int index = stats.get_bb_eval() + 1;
		  	boolean isBetter = false;
		  	
		  	for(int i=0 ; i<4 ; i++){
		  		if(Math.fabs(x.value(i)-bestX[i]) > minGain){
		  			// le gain est suffisant pour être comptabilisé comme un progrès
		  			isBetter = true;
		  			break;
		  		}
		  	}
		  	
		  	if(isBetter){
		  		this.last_succes_index = index;
		  		for(int i=0 ; i<4 ; i++){
		  			bestX[i] = x.value(i);
		  		}
		  	}
		  }
		 */
	}

	private void initAllParams(AllParameters p) {
		int n = 4;
		p.setAttributeValueSizeT("DIMENSION", n);

		// variable x = [b, h, s, a]
		//  Lower bound
		ArrayOfDouble lb = new ArrayOfDouble(n);
		lb.set(0, new Double(46E-3));
		lb.set(1, new Double(80E-3));
		lb.set(2, new Double(5.2E-3));
		lb.set(3, new Double(3.8E-3));
		p.setAttributeValueAOD("LOWER_BOUND", lb);

		// Upper bound
		ArrayOfDouble ub = new ArrayOfDouble(n);
		ub.set(0, new Double(73E-3));
		ub.set(1, new Double(140E-3));
		ub.set(2, new Double(6.9E-3));
		ub.set(3, new Double(4.7E-3));
		p.setAttributeValueAOD("UPPER_BOUND", ub);

		// Starting point
		Point x0 = new Point(n);
		x0.set(0, new Double(55E-3));
		x0.set(1, new Double(100E-3));
		x0.set(2, new Double(6E-3));
		x0.set(3, new Double(4.2E-3));
		p.setAttributeValuePoint("X0", x0);

		// Max bb eval
		p.setAttributeValueSizeT("MAX_BB_EVAL", 200);

		// BB input
		BBInputTypeList bbInputTypeList = new BBInputTypeList();
		bbInputTypeList.add(BBInputType.CONTINUOUS);
		bbInputTypeList.add(BBInputType.CONTINUOUS);
		bbInputTypeList.add(BBInputType.CONTINUOUS);
		bbInputTypeList.add(BBInputType.CONTINUOUS);
		p.setAttributeValueBBInputTypeList("BB_INPUT_TYPE", bbInputTypeList);

		// BB output
		BBOutputTypeList bbOutputTypeList = new BBOutputTypeList();
		bbOutputTypeList.add(new BBOutputType("OBJ"));
		bbOutputTypeList.add(new BBOutputType("EB"));
		bbOutputTypeList.add(new BBOutputType("EB"));
		p.setAttributeValueBBOutputTypeList("BB_OUTPUT_TYPE", bbOutputTypeList);

		// Display parameters
		p.setAttributeValueInt("DISPLAY_DEGREE", 2);
		p.setAttributeValueBool("DISPLAY_ALL_EVAL", true);
		p.setAttributeValueAOS("DISPLAY_STATS", new ArrayOfString("EVAL ( SOL ) OBJ CONS_H H_MAX", " "));

		p.checkAndComply();
	}
	
	 private void userIterationCallback(Step step, boolean stop) {
		 System.out.println("Callback");
	 }

	public OptimIBeam() {
		try {

			/// The main step is in charge to solve the optimization problem
			MainStep mainstep = new MainStep();

			// The init command must be done first
			// !!!!! Important to do init FIRST (before p reading) to set the Locale to US
			// !!!!
			// Nomad does not support decimal separator other than "."
			mainstep.init();

			AllParameters p = new AllParameters();
			// Parameters can be set directly in the AllParameters object or by reading
			// strings as would be done when reading the parameter file. For complex
			// problems, it may be necessary to be able to define the parameters directly.
			// This is the approach taken in the following. However, please remind that for
			// simple problems, the first method may be more efficient.
			// Example:
			// p.readParamLine("LOWER_BOUND ( -1.5 -0.5 )");
			// p.readParamLine("UPPER_BOUND ( 1.5 2.5 )");
			// p.readParamLine("X0 ( 0.1 0.1 )");
			initAllParams(p);

			mainstep.setAllParameters(p);

			// custom evaluator creation:
			My_Evaluator ev = new My_Evaluator(p);
			mainstep.addEvaluator(ev);
			
			SWIGTYPE_p_StepCbFunc userIterationCallback;
				
			mainstep.addCallback(CallbackType.MEGA_ITERATION_END, userIterationCallback);

			mainstep.start();
			mainstep.run();
			mainstep.end();

			// Get the best feasibles
			EvalPointVector bestFeas = new EvalPointVector();
			CacheBase cacheBase = CacheBase.getInstance();

			cacheBase.findBestFeas(bestFeas);

			for (int i = 0; i < bestFeas.size(); i++) {
				EvalPoint ep = bestFeas.get(i);
				System.out.print("Solution : ");
				double var;
				String[] lbl = new String[] { "b", "h", "s", "a" };
				for (int j = 0; j < ep.size(); j++) {
					var = ep.get(j).todouble() * 1000;
					System.out.printf("%s = %.3f mm, ", lbl[j], var);
				}
				System.out.println();
				Eval eval = ep.getEval(EvalType.BB);
				double mass = eval.getObjective().todouble();
				System.out.printf("Mass = %.3f kg", mass);
			}

		} catch (RuntimeException e) {
			System.err.println("\nNOMAD has been interrupted (" + e.toString() + ")\n\n");
		}

	}

	public static void main(String args[]) {
		System.out.println("Starting test...");
		new OptimIBeam();
	}
}
