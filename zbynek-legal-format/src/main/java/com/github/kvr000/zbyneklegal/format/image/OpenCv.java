package com.github.kvr000.zbyneklegal.format.image;

import lombok.SneakyThrows;
import nu.pattern.OpenCV;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.Scalar;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import javax.inject.Singleton;


@Singleton
public class OpenCv
{
	static {
		OpenCV.loadShared();
	}

	static final Imgcodecs imageCodecs = new Imgcodecs();

	Mat loadImage(byte[] imageData)
	{
		Mat image = imageCodecs.imdecode(new MatOfByte(imageData), Imgcodecs.IMREAD_COLOR);
		return image;
	}
}
