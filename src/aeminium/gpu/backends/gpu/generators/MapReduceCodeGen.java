package aeminium.gpu.backends.gpu.generators;

import java.util.HashMap;

import aeminium.gpu.backends.gpu.GPUReduce;
import aeminium.gpu.backends.gpu.buffers.BufferHelper;
import aeminium.gpu.templates.Template;
import aeminium.gpu.templates.TemplateWrapper;

@SuppressWarnings("rawtypes")
public class MapReduceCodeGen extends AbstractReduceCodeGen {
	private GPUReduce op;
	private String[] map_parameters;

	public MapReduceCodeGen(GPUReduce op) {
		this.op = op;
		id = op.getMapFun().getId() + "_" + op.getReduceFun().getId();
		map_parameters = op.getMapFun().getParameters();
	}

	public String getMapLambdaSource() {
		HashMap<String, String> mapping = new HashMap<String, String>();
		mapping.put("input_type", BufferHelper.getCLTypeOf(op.getInputType()));
		mapping.put("output_type", BufferHelper.getCLTypeOf(op.getOutputType()));
		mapping.put("map_lambda_name", getMapLambdaName());
		mapping.put("map_lambda_par", map_parameters[0]);
		mapping.put("source", op.getMapFun().getSource());
		mapping.put("extra_args", getExtraArgs());
		Template t = new Template(
				new TemplateWrapper("opencl/MapLambdaDef.clt"));
		return t.apply(mapping);
	}

	public String getReduceLambdaSource() {
		HashMap<String, String> mapping = new HashMap<String, String>();
		mapping.put("input_type", BufferHelper.getCLTypeOf(op.getOutputType()));
		mapping.put("output_type", BufferHelper.getCLTypeOf(op.getOutputType()));
		mapping.put("reduce_lambda_name", getReduceLambdaName());
		mapping.put("reduce_lambda_par1", op.getReduceFun().getParameters()[0]);
		mapping.put("reduce_lambda_par2", op.getReduceFun().getParameters()[1]);
		mapping.put("source", op.getReduceFun().getSource());
		mapping.put("extra_args", getExtraArgs());
		mapping.put("extra_args_call", getExtraArgsCall());
		Template t = new Template(new TemplateWrapper(
				"opencl/ReduceLambdaDef.clt"));
		return t.apply(mapping);
	}

	public String getReduceKernelSource() {
		HashMap<String, String> mapping = new HashMap<String, String>();

		mapping.put("input_type", BufferHelper.getCLTypeOf(op.getInputType()));
		mapping.put("output_type", BufferHelper.getCLTypeOf(op.getOutputType()));

		mapping.put("map_lambda_name", getMapLambdaName());
		mapping.put("map_lambda_def", getMapLambdaSource());

		mapping.put("reduce_lambda_name", getReduceLambdaName());
		mapping.put("reduce_lambda_def", getReduceLambdaSource());

		mapping.put("reduce_kernel_name", getReduceKernelName());
		mapping.put("other_sources", op.getOtherSources());

		mapping.put("extra_args", getExtraArgs());
		mapping.put("extra_args_call", getExtraArgsCall());
		
		if (isRange) {
			mapping.put("get_input", "inputOffset");
		} else {
			mapping.put("get_input", "map_input[inputOffset]");
		}

		mapping.put("seed_source", op.getOpenCLSeed());

		Template t = new Template(new TemplateWrapper(
				"opencl/MapReduceKernel.clt"));
		return t.apply(mapping);
	}

	public String getMapLambdaName() {
		return "map_function_" + op.getMapFun().getId();
	}

	public String getReduceLambdaName() {
		return "reduce_function_" + op.getReduceFun().getId();
	}

	public String getReduceKernelName() {
		return "reduce_kernel_" + id;
	}

}
