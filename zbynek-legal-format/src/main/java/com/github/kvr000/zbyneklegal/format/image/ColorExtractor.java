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
public class ColorExtractor
{
	static {
		OpenCV.loadShared();
	}
	private static final Imgcodecs imageCodecs = new Imgcodecs();

	@SneakyThrows
	public byte[] extractColor(byte[] imageData, byte[] lowHsv, byte[] highHsv)
	{
		Mat image = loadImage(imageData);
		Mat imageTransparent = new Mat(image.rows(), image.cols(), CvType.CV_8UC4);
		Imgproc.cvtColor(image, imageTransparent, Imgproc.COLOR_BGR2BGRA);
		Scalar lowScalar = new Scalar(lowHsv[0] & 0xff, lowHsv[1] & 0xff, lowHsv[2] & 0xff);
		Scalar highScalar = new Scalar(highHsv[0] & 0xff, highHsv[1] & 0xff, highHsv[2] & 0xff);
		Mat hsv = new Mat(image.rows(), image.cols(), CvType.CV_8UC4);
		Imgproc.cvtColor(image, hsv, Imgproc.COLOR_BGR2HSV);
		Mat resultHsv = new Mat(image.rows(), image.cols(), CvType.CV_8UC4);
		Core.inRange(hsv, lowScalar, highScalar, resultHsv);
		Mat resultBgr = new Mat(image.rows(), image.cols(), CvType.CV_8UC4); resultBgr.setTo(new Scalar(255, 255, 255, 0));
		imageTransparent.copyTo(resultBgr, resultHsv);
		MatOfByte output = new MatOfByte();
		imageCodecs.imencode(".png", resultBgr, output);
		return output.toArray();
	}

	public byte[] hsvToBytes(float hue360, float saturation1, float value1)
	{
		return new byte[]{ (byte)(int) (hue360/360*256), (byte)(int) (saturation1*255), (byte)(int) (value1*255) };
	}

	private Mat loadImage(byte[] imageData)
	{
		Mat image = imageCodecs.imdecode(new MatOfByte(imageData), Imgcodecs.IMREAD_COLOR);
		return image;
	}
}
