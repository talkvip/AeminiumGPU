package aeminium.gpu.operations;

import aeminium.gpu.buffers.BufferHelper;
import aeminium.gpu.devices.GPUDevice;
import aeminium.gpu.executables.GenericProgram;
import aeminium.gpu.executables.Program;
import aeminium.gpu.lists.PList;
import aeminium.gpu.lists.lazyness.LazyEvaluator;
import aeminium.gpu.lists.lazyness.LazyPList;
import aeminium.gpu.operations.functions.LambdaMapper;
import aeminium.gpu.operations.generator.MapCodeGen;

import com.nativelibs4java.opencl.CLBuffer;
import com.nativelibs4java.opencl.CLContext;
import com.nativelibs4java.opencl.CLEvent;
import com.nativelibs4java.opencl.CLQueue;

public class Map<I,O> extends GenericProgram implements Program {
	
	protected PList<I> input;
	private PList<O> output;
	protected LambdaMapper<I,O> mapFun;
	
	protected CLBuffer<?> inbuffer;
	private CLBuffer<?> outbuffer;
	
	private MapCodeGen gen;
	
	// Constructors
	
	public Map(LambdaMapper<I, O> mapFun2, PList<I> list, GPUDevice dev) {
		this(mapFun2, list, "", dev);
	}
	
	public Map(LambdaMapper<I, O> mapFun, PList<I> list, String other, GPUDevice dev) {
		this.device = dev;
		this.input = list;
		this.mapFun = mapFun;
		this.setOtherSources(other);
		gen = new MapCodeGen(this);
	}
	
	// Pipeline
	
	@Override
	protected String getSource() {
		return gen.getMapKernelSource();
	}
	
	public String getMapOpenCLSource() {
		return gen.getMapLambdaSource();
	}
	
	@Override
	public void prepareBuffers(CLContext ctx) {
		inbuffer = BufferHelper.createInputBufferFor(ctx, input);
		outbuffer = BufferHelper.createOutputBufferFor(ctx, getOutputType(), input.size());
	}

	@Override
	public void execute(CLContext ctx, CLQueue q) {
		synchronized (kernel) {
		    // setArgs will throw an exception at runtime if the types / sizes of the arguments are incorrect
		    kernel.setArgs(inbuffer, outbuffer);

		    // Ask for 1-dimensional execution of length dataSize, with auto choice of local workgroup size :
		    // TODO: WorkGroup Size
		    kernelCompletion = kernel.enqueueNDRange(q, new int[] { input.size() }, new CLEvent[] {});
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	public void retrieveResults(CLContext ctx, CLQueue q) {
		output = (PList<O>) BufferHelper.extractFromBuffer(outbuffer, q, kernelCompletion, getOutputType(), input.size());
	}

	
	@Override
	public void release() {
		super.release();
		this.inbuffer.release();
		this.outbuffer.release();
	}

	// Output
	
	public PList<O> getOutput() {
		// Reference to Map operation
		if (output != null) return output;
		
		final Map<I,O> innerMap = this;
		
		// Lazy return
		LazyEvaluator<O> operation = new LazyEvaluator<O>() {

			@Override
			public PList<O> evaluate() {
				innerMap.run();
				return output;
			}

			@Override
			public Class<?> getType() {
				return input.getType();
			}

			@Override
			public <K> boolean canMergeWithMap(LambdaMapper<O, K> mapFun) {
				return false;
			}

			@Override
			public <K> PList<K> mergeWithMap(Map<O, K> mapOp) {
				// TODO: Merge
				return null;
			}
			
		};
		return new LazyPList<O>(operation, input.size());
	}
	
	
	// Utils
	
	public String getInputType() {
		return input.getType().getSimpleName().toString();
	}
	
	public String getOutputType() {
		Class<?> klass = mapFun.getClass();
		try {
			return klass.getMethod("map", input.getType()).getReturnType().getSimpleName().toString();
		} catch (SecurityException e) {
			e.printStackTrace();
			return null;
		} catch (NoSuchMethodException e) {
			// Java sucks
			// TODO: Compiler can give us this information.
			System.out.println("AeminiumGPU Runtime does not support generic types on Lambdas.");
			System.exit(0);
			return null;
		}
	}
	
	public int getOutputSize() {
		return input.size();
	}
	

	// Getters and Setters
	
	public void setOutput(PList<O> output) {
		this.output = output;
	}

	public LambdaMapper<I, O> getMapFun() {
		return mapFun;
	}

	public void setMapFun(LambdaMapper<I, O> mapFun) {
		this.mapFun = mapFun;
	}
	
	@Override
	public String getKernelName() {
		return gen.getMapKernelName();
	}
}