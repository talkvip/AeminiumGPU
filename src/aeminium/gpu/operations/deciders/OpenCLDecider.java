package aeminium.gpu.operations.deciders;

import aeminium.gpu.devices.CPUDevice;
import aeminium.gpu.recorder.Configuration;


/*
import java.util.StringTokenizer;

import weka.classifiers.Classifier;
import weka.classifiers.CostMatrix;
import weka.classifiers.meta.CostSensitiveClassifier;
import weka.classifiers.rules.DecisionTable;
import weka.core.DenseInstance;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.converters.ConverterUtils.DataSource;
*/

public class OpenCLDecider {

	private static final int LOOP_LIMIT = 20;
	private static final boolean USE_ML = System.getenv().containsKey("ML");

	public static int getSplitPoint(int units, int size, int rsize, String code,
			String complexity, String features) {
		
		if (System.getenv().containsKey("DEBUG_ML")) {
			System.out.print("Features:");
			System.out.println(features);
		}

		if (USE_ML) {
			return OpenCLDecider.decide(units, size, features);
		} else {
			return OpenCLDecider.decide(units, size, rsize, code, complexity, false);
		}
	}
	
	
	// DECIDES
	public static int decide(int units, int size, String features) {
		
		if (size < 5000) { // Small sizes are for CPU
			return units;
		}
		
		if (features == null || features.length() == 0) {
			// Dumb heuristic in case features are absent
			if (size > 10000) {
				return 0;
			}
			return units;
		}
		return units;
	}
	
	public static int decide(int units, int size, int rsize, String code,
			String complexity, boolean isRange) {
		int tasks_per_cpu_core = units / (10 * CPUDevice.getParallelism());
		int defaultSplit = units - (tasks_per_cpu_core * CPUDevice.getParallelism());
		
		if (complexity == null || complexity.length() == 0) {
			return defaultSplit;
		}

		try {
			long gpuTime = getGPUEstimation(size, rsize, code, complexity,
					isRange);
			long cpuTime = getCPUEstimation(size, rsize, code, complexity,
					isRange);

			if (System.getenv("BENCH") != null) {
				System.out.println("> GPUexp: " + gpuTime);
				System.out.println("> CPUexp: " + cpuTime);
			}
			
			if (cpuTime < gpuTime) {
				return 0;
			} else {
				return defaultSplit;
			}
		} catch (Exception e) {
			if (System.getenv("DEBUG") != null) {
				System.out.println("Failed to to consider GPU vs CPU.");
				e.printStackTrace();
			}
			return 0;
		}

	}

	public static long getCPUEstimation(int size, int rsize, String code,
			String complexity, boolean isRange) {
		long pTimeCPU = 0;
		String[] parts = complexity.split("\\+");
		for (String part : parts) {
			String[] kv = part.split("\\*");
			try {
				int times = Integer.parseInt(kv[0]);
				String v = OpenCLDecider.sameOperationAs(kv[1]);
				if (v.equals("fieldaccess"))
					continue;
				if ((v.equals("mul") || v.equals("pow")) && times < LOOP_LIMIT)
					continue;
				if (v.equals("sin") || v.equals("cos")) {
					times *= 2.77;
				} else {
					times *= 0.6;
				}
				pTimeCPU += times
						* (getInterpolatedValue("cpu.execution.", size, "." + v));
			} catch (Exception e) {
				System.out.println("Failed to get " + part + ":>" + e);
				e.printStackTrace();
			}

		}
		return pTimeCPU;
	}

	private static String sameOperationAs(String op) {
		if (op.equals("div") || op.equals("plus") || op.equals("minus")
				|| op.equals("mod") || op.equals("lt") || op.equals("gt"))
			return "mul";
		if (op.equals("cos"))
			return "sin";
		return op;
	}

	public static long getGPUEstimation(int size, int rsize, String code,
			String complexity, boolean isRange) {
		long pTimeGPU = 0;
		// Buffer times
		if (!isRange) {
			pTimeGPU += getInterpolatedValue("gpu.buffer.to.", size, "");
		}
		if (rsize > 1) {
			pTimeGPU += getInterpolatedValue("gpu.buffer.from.", rsize, "");
		}

		// Using cache
		// pTimeGPU += getInterpolatedValue("gpu.kernel.compilation.", size,
		// ".plus");

		String[] parts = complexity.split("\\+");
		long mx = 0;
		for (String part : parts) {
			String[] kv = part.split("\\*");
			try {
				int times = Integer.parseInt(kv[0]);
				String v = OpenCLDecider.sameOperationAs(kv[1]);
				if (v.equals("fieldaccess"))
					continue;
				mx = Math
						.max(mx,
								times
										* (getInterpolatedValue(
												"gpu.kernel.execution.", size,
												"." + v)));
			} catch (Exception e) {
				if (System.getenv("DEBUG") != null) {
					System.out.println("Failed to get " + part);
				}
			}
		}
		pTimeGPU += mx;
		return pTimeGPU;
	}

	public static long getInterpolatedValue(String prefix, int size,
			String sufix) {

		int sb = (int) Math.pow(10, Math.floor(Math.log10(size)));
		int st = (int) Math.pow(10, Math.ceil(Math.log10(size)));

		float cutPoint = size / ((float) st);
		long bottom = 0;
		long top = 0;
		try {
			bottom = getOrFail(prefix + sb + sufix);
		} catch (Exception e) {
			return 0;
		}
		try {
			top = getOrFail(prefix + st + sufix);
			if (top == 0)
				throw new Exception();
		} catch (Exception e) {
			return bottom;
		}
		return (long) (bottom * cutPoint + top * (1 - cutPoint));
	}

	private static long getOrFail(String key) {
		String val = Configuration.get(key);
		try {
			return Long.parseLong(val);
		} catch (java.lang.NumberFormatException e) {
			if (System.getenv("DEBUG") != null) {
				System.out.println("Failed to load bench value for " + key
						+ ", value: '" + val + "'.");
			}
			return 0;
		}
	}
	/*
	private static Classifier classifier = null;
	private static Classifier getClassifier() throws Exception {
		if (OpenCLDecider.classifier != null) return OpenCLDecider.classifier; 
		// Alcides Fonseca and Bruno Cabral,AeminiumGPU: An Intelligent Framework for GPU Programming, in Facing the Multicore-Challenge III, 2012
	    Instances randData = DataSource.read("dataset/features_processed.arff");
	    randData.setClassIndex(randData.numAttributes() - 1);
	    
	    CostSensitiveClassifier c = new CostSensitiveClassifier();
	    c.setMinimizeExpectedCost(true);
	    c.setClassifier(new DecisionTable());
	    
	    CostMatrix cm = new CostMatrix(2);
	    cm.initialize();
	    cm.setElement(0, 1, 0.4);
	    cm.setElement(1, 0, 0.6);
	    c.setCostMatrix(cm);
	    OpenCLDecider.classifier = c;
		return c;
	}*/
	
}
