package aeminium.gpu.examples;

import aeminium.gpu.collections.matrices.IntMatrix;
import aeminium.gpu.collections.matrices.PMatrix;
import aeminium.gpu.operations.functions.LambdaMapper;
import aeminium.gpu.operations.functions.LambdaReducer;

public class MatrixExample {
	public static void main(String[] args) {
		
		PMatrix<Integer> m = new IntMatrix(10,10);
		
		for (int i=0;i<10;i++) {
			for (int j=0;j<10;j++) {
				m.set(i,j,0);
			}
		}
		
		
		m = m.map(new LambdaMapper<Integer, Integer>() {

			@Override
			public Integer map(Integer input) {
				return input+1;
			}
			
			public String getSource() {
				return "return input+1;";
			}
		});
		
		int i = m.reduce(new LambdaReducer<Integer>() {
			
			public String getSource() {
				return "return reduce_input_first + reduce_input_second;";
			}
			
			@Override
			public Integer combine(Integer input, Integer other) {
				return input+other;
			}

			@Override
			public Integer getSeed() {
				return 0;
			}
		});
		
		System.out.println("Number of cells:" + i);
		
	}
}
