package com.github.kvr000.zbyneklegal.format.image;

import lombok.RequiredArgsConstructor;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import javax.inject.Inject;
import javax.inject.Singleton;


@Singleton
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class ImageEdit
{
	private static final Imgcodecs imageCodecs = new Imgcodecs();

	private final OpenCv openCv;

	public byte[] resizeImage(byte[] imageData, int width, int height, String extension)
	{
		Mat image = openCv.loadImage(imageData);
		Mat dst = new Mat(width, height, CvType.CV_8UC3);
		Imgproc.resize(image, dst, new Size(width, height));
		MatOfByte output = new MatOfByte();
		imageCodecs.imencode(extension, dst, output);
		return output.toArray();
	}
}
