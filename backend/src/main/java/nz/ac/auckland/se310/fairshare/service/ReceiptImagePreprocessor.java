package nz.ac.auckland.se310.fairshare.service;

import org.springframework.stereotype.Component;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;

/**
 * Normalises phone photos into high-contrast, deskewed binary images suitable for OCR.
 */
@Component
public class ReceiptImagePreprocessor {

    static final int TARGET_DPI = 300;
    private static final int MIN_OCR_WIDTH = 1400;
    private static final int MAX_OCR_WIDTH = 2400;
    private static final int RECEIPT_MASK_MAX_DIMENSION = 400;
    private static final int ADAPTIVE_THRESHOLD_OFFSET = 12;
    private static final int MIN_DESPECKLE_COMPONENT_SIZE = 3;
    private static final double MAX_SKEW_DEGREES = 7.0;
    private static final double SKEW_STEP_DEGREES = 0.5;

    public BufferedImage preprocess(BufferedImage source) {
        BufferedImage scaled = rescaleForOcr(source);
        BufferedImage grayscale = toGrayscale(scaled);
        BufferedImage receiptRegion = isolateReceiptRegion(grayscale);
        BufferedImage denoised = medianDenoise(receiptRegion);
        BufferedImage binary = binarize(denoised);
        BufferedImage despeckled = despeckle(binary);
        BufferedImage morphologicallyCleaned = closeSmallGaps(despeckled);
        BufferedImage borderCleaned = removeBorderArtifacts(morphologicallyCleaned);
        return removeBorderArtifacts(deskew(borderCleaned));
    }

    BufferedImage rescaleForOcr(BufferedImage source) {
        int sourceWidth = source.getWidth();
        double scale = sourceWidth < MIN_OCR_WIDTH
                ? (double) MIN_OCR_WIDTH / sourceWidth
                : sourceWidth > MAX_OCR_WIDTH
                        ? (double) MAX_OCR_WIDTH / sourceWidth
                        : 1.0;

        int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
        BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = scaled.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, width, height);
            graphics.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(
                    RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return scaled;
    }

    BufferedImage toGrayscale(BufferedImage source) {
        BufferedImage grayscale = new BufferedImage(
                source.getWidth(), source.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        WritableRaster raster = grayscale.getRaster();
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                Color color = new Color(source.getRGB(x, y), true);
                int luminance = (int) Math.round(
                        0.2126 * color.getRed()
                                + 0.7152 * color.getGreen()
                                + 0.0722 * color.getBlue());
                raster.setSample(x, y, 0, luminance);
            }
        }
        return grayscale;
    }

    BufferedImage isolateReceiptRegion(BufferedImage grayscale) {
        BufferedImage maskSample = createMaskSample(grayscale);
        int maskWidth = maskSample.getWidth();
        int maskHeight = maskSample.getHeight();
        int paperThreshold = Math.max(35, otsuThreshold(maskSample) - 15);
        int[] labels = new int[maskWidth * maskHeight];
        int nextLabel = 0;
        int largestLabel = 0;
        int largestSize = 0;
        ArrayDeque<Integer> pending = new ArrayDeque<>();

        for (int y = 0; y < maskHeight; y++) {
            for (int x = 0; x < maskWidth; x++) {
                int start = y * maskWidth + x;
                if (labels[start] != 0
                        || maskSample.getRaster().getSample(x, y, 0) <= paperThreshold) {
                    continue;
                }

                nextLabel++;
                labels[start] = nextLabel;
                pending.add(start);
                int componentSize = 0;
                while (!pending.isEmpty()) {
                    int position = pending.remove();
                    componentSize++;
                    int componentX = position % maskWidth;
                    int componentY = position / maskWidth;
                    enqueuePaperPixel(
                            maskSample, componentX - 1, componentY,
                            paperThreshold, nextLabel, labels, pending);
                    enqueuePaperPixel(
                            maskSample, componentX + 1, componentY,
                            paperThreshold, nextLabel, labels, pending);
                    enqueuePaperPixel(
                            maskSample, componentX, componentY - 1,
                            paperThreshold, nextLabel, labels, pending);
                    enqueuePaperPixel(
                            maskSample, componentX, componentY + 1,
                            paperThreshold, nextLabel, labels, pending);
                }

                if (componentSize > largestSize) {
                    largestSize = componentSize;
                    largestLabel = nextLabel;
                }
            }
        }

        if (largestSize < maskWidth * maskHeight / 12) {
            return grayscale;
        }

        int[] rowLeft = new int[maskHeight];
        int[] rowRight = new int[maskHeight];
        Arrays.fill(rowLeft, maskWidth);
        Arrays.fill(rowRight, -1);
        int top = maskHeight;
        int bottom = -1;
        for (int y = 0; y < maskHeight; y++) {
            for (int x = 0; x < maskWidth; x++) {
                if (labels[y * maskWidth + x] == largestLabel) {
                    rowLeft[y] = Math.min(rowLeft[y], x);
                    rowRight[y] = Math.max(rowRight[y], x);
                    top = Math.min(top, y);
                    bottom = Math.max(bottom, y);
                }
            }
        }
        fillMissingRowBounds(rowLeft, rowRight, top, bottom);

        int sourceWidth = grayscale.getWidth();
        int sourceHeight = grayscale.getHeight();
        int horizontalPadding = Math.max(2, sourceWidth / 200);
        int verticalPadding = Math.max(2, sourceHeight / 200);
        int cropTop = Math.max(0, top * sourceHeight / maskHeight - verticalPadding);
        int cropBottom = Math.min(
                sourceHeight - 1,
                ((bottom + 1) * sourceHeight + maskHeight - 1) / maskHeight
                        + verticalPadding);
        int cropLeft = sourceWidth - 1;
        int cropRight = 0;
        for (int y = top; y <= bottom; y++) {
            cropLeft = Math.min(
                    cropLeft,
                    rowLeft[y] * sourceWidth / maskWidth - horizontalPadding);
            cropRight = Math.max(
                    cropRight,
                    ((rowRight[y] + 1) * sourceWidth + maskWidth - 1) / maskWidth
                            + horizontalPadding);
        }
        cropLeft = Math.max(0, cropLeft);
        cropRight = Math.min(sourceWidth - 1, cropRight);

        BufferedImage isolated = new BufferedImage(
                cropRight - cropLeft + 1,
                cropBottom - cropTop + 1,
                BufferedImage.TYPE_BYTE_GRAY);
        WritableRaster source = grayscale.getRaster();
        WritableRaster target = isolated.getRaster();
        for (int destinationY = 0; destinationY < isolated.getHeight(); destinationY++) {
            int sourceY = destinationY + cropTop;
            int maskY = Math.min(maskHeight - 1, sourceY * maskHeight / sourceHeight);
            int paperLeft = Math.max(
                    0, rowLeft[maskY] * sourceWidth / maskWidth - horizontalPadding);
            int paperRight = Math.min(
                    sourceWidth - 1,
                    ((rowRight[maskY] + 1) * sourceWidth + maskWidth - 1) / maskWidth
                            + horizontalPadding);
            for (int destinationX = 0; destinationX < isolated.getWidth(); destinationX++) {
                int sourceX = destinationX + cropLeft;
                int value = sourceX >= paperLeft && sourceX <= paperRight
                        ? source.getSample(sourceX, sourceY, 0)
                        : 255;
                target.setSample(destinationX, destinationY, 0, value);
            }
        }
        return isolated;
    }

    private BufferedImage createMaskSample(BufferedImage grayscale) {
        double scale = Math.min(
                1.0,
                (double) RECEIPT_MASK_MAX_DIMENSION
                        / Math.max(grayscale.getWidth(), grayscale.getHeight()));
        int width = Math.max(1, (int) Math.round(grayscale.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(grayscale.getHeight() * scale));
        BufferedImage sample = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D graphics = sample.createGraphics();
        try {
            graphics.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.drawImage(grayscale, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return meanBlur(sample, 2);
    }

    private BufferedImage meanBlur(BufferedImage grayscale, int radius) {
        BufferedImage blurred = copyGrayImage(grayscale);
        WritableRaster target = blurred.getRaster();
        long[][] integral = integralImage(grayscale);
        for (int y = 0; y < grayscale.getHeight(); y++) {
            for (int x = 0; x < grayscale.getWidth(); x++) {
                int left = Math.max(0, x - radius);
                int right = Math.min(grayscale.getWidth() - 1, x + radius);
                int top = Math.max(0, y - radius);
                int bottom = Math.min(grayscale.getHeight() - 1, y + radius);
                int area = (right - left + 1) * (bottom - top + 1);
                target.setSample(
                        x, y, 0,
                        rectangleSum(integral, left, top, right, bottom) / area);
            }
        }
        return blurred;
    }

    private void enqueuePaperPixel(
            BufferedImage image,
            int x,
            int y,
            int threshold,
            int label,
            int[] labels,
            ArrayDeque<Integer> pending) {
        if (x < 0 || y < 0 || x >= image.getWidth() || y >= image.getHeight()) {
            return;
        }
        int position = y * image.getWidth() + x;
        if (labels[position] != 0 || image.getRaster().getSample(x, y, 0) <= threshold) {
            return;
        }
        labels[position] = label;
        pending.add(position);
    }

    private void fillMissingRowBounds(
            int[] rowLeft, int[] rowRight, int top, int bottom) {
        for (int y = top + 1; y <= bottom; y++) {
            if (rowRight[y] < 0) {
                rowLeft[y] = rowLeft[y - 1];
                rowRight[y] = rowRight[y - 1];
            }
        }
        for (int y = bottom - 1; y >= top; y--) {
            if (rowRight[y] < 0) {
                rowLeft[y] = rowLeft[y + 1];
                rowRight[y] = rowRight[y + 1];
            }
        }
    }

    BufferedImage medianDenoise(BufferedImage grayscale) {
        BufferedImage denoised = copyGrayImage(grayscale);
        WritableRaster source = grayscale.getRaster();
        WritableRaster target = denoised.getRaster();
        int[] neighbourhood = new int[9];

        for (int y = 1; y < grayscale.getHeight() - 1; y++) {
            for (int x = 1; x < grayscale.getWidth() - 1; x++) {
                int index = 0;
                for (int offsetY = -1; offsetY <= 1; offsetY++) {
                    for (int offsetX = -1; offsetX <= 1; offsetX++) {
                        neighbourhood[index++] = source.getSample(x + offsetX, y + offsetY, 0);
                    }
                }
                Arrays.sort(neighbourhood);
                target.setSample(x, y, 0, neighbourhood[4]);
            }
        }
        return denoised;
    }

    BufferedImage binarize(BufferedImage grayscale) {
        BufferedImage binary = new BufferedImage(
                grayscale.getWidth(), grayscale.getHeight(), BufferedImage.TYPE_BYTE_BINARY);
        WritableRaster source = grayscale.getRaster();
        WritableRaster target = binary.getRaster();
        long[][] integral = integralImage(grayscale);
        int radius = Math.max(15, Math.min(50,
                Math.min(grayscale.getWidth(), grayscale.getHeight()) / 50));

        for (int y = 0; y < grayscale.getHeight(); y++) {
            for (int x = 0; x < grayscale.getWidth(); x++) {
                int left = Math.max(0, x - radius);
                int right = Math.min(grayscale.getWidth() - 1, x + radius);
                int top = Math.max(0, y - radius);
                int bottom = Math.min(grayscale.getHeight() - 1, y + radius);
                long sum = rectangleSum(integral, left, top, right, bottom);
                int area = (right - left + 1) * (bottom - top + 1);
                double localMean = (double) sum / area;
                boolean foreground = source.getSample(x, y, 0)
                        < localMean - ADAPTIVE_THRESHOLD_OFFSET;
                target.setSample(x, y, 0, foreground ? 0 : 1);
            }
        }
        return binary;
    }

    /**
     * Removes tiny isolated foreground components from a binary receipt image.
     *
     * The grayscale median filter above handles single-pixel intensity noise,
     * but small dark blobs can survive until thresholding turns them into black
     * foreground.  Removing them here, before morphology can join them to text,
     * keeps those blobs from becoming stray OCR characters.
     */
    BufferedImage despeckle(BufferedImage binary) {
        BufferedImage cleaned = copyBinaryImage(binary);
        boolean[][] visited = new boolean[binary.getHeight()][binary.getWidth()];
        WritableRaster raster = cleaned.getRaster();
        int[][] directions = {
                {-1, -1}, {0, -1}, {1, -1},
                {-1, 0},            {1, 0},
                {-1, 1},  {0, 1},   {1, 1}
        };

        for (int y = 0; y < binary.getHeight(); y++) {
            for (int x = 0; x < binary.getWidth(); x++) {
                if (visited[y][x] || !isBlack(binary, x, y)) {
                    continue;
                }

                List<Pixel> component = new ArrayList<>();
                Queue<Pixel> queue = new ArrayDeque<>();
                visited[y][x] = true;
                queue.add(new Pixel(x, y));
                int minX = x;
                int maxX = x;
                int minY = y;
                int maxY = y;

                while (!queue.isEmpty()) {
                    Pixel pixel = queue.remove();
                    component.add(pixel);
                    minX = Math.min(minX, pixel.x());
                    maxX = Math.max(maxX, pixel.x());
                    minY = Math.min(minY, pixel.y());
                    maxY = Math.max(maxY, pixel.y());

                    for (int[] direction : directions) {
                        int neighbourX = pixel.x() + direction[0];
                        int neighbourY = pixel.y() + direction[1];
                        if (neighbourX < 0 || neighbourY < 0
                                || neighbourX >= binary.getWidth()
                                || neighbourY >= binary.getHeight()
                                || visited[neighbourY][neighbourX]
                                || !isBlack(binary, neighbourX, neighbourY)) {
                            continue;
                        }
                        visited[neighbourY][neighbourX] = true;
                        queue.add(new Pixel(neighbourX, neighbourY));
                    }
                }

                int componentWidth = maxX - minX + 1;
                int componentHeight = maxY - minY + 1;
                if (componentWidth < MIN_DESPECKLE_COMPONENT_SIZE
                        && componentHeight < MIN_DESPECKLE_COMPONENT_SIZE) {
                    for (Pixel pixel : component) {
                        raster.setSample(pixel.x(), pixel.y(), 0, 1);
                    }
                }
            }
        }
        return cleaned;
    }

    BufferedImage deskew(BufferedImage binary) {
        double angle = estimateSkewAngle(binary);
        if (Math.abs(angle) < SKEW_STEP_DEGREES) {
            return binary;
        }

        BufferedImage rotated = whiteBinaryImage(binary.getWidth(), binary.getHeight());
        Graphics2D graphics = rotated.createGraphics();
        try {
            graphics.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            graphics.rotate(
                    Math.toRadians(-angle),
                    binary.getWidth() / 2.0,
                    binary.getHeight() / 2.0);
            graphics.drawImage(binary, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return rotated;
    }

    double estimateSkewAngle(BufferedImage binary) {
        int width = binary.getWidth();
        int height = binary.getHeight();
        int marginX = Math.max(1, width / 50);
        int marginY = Math.max(1, height / 50);
        int sampleStep = Math.max(1, Math.max(width, height) / 800);
        double bestAngle = 0;
        long bestScore = Long.MIN_VALUE;

        for (double angle = -MAX_SKEW_DEGREES;
                angle <= MAX_SKEW_DEGREES;
                angle += SKEW_STEP_DEGREES) {
            double slope = Math.tan(Math.toRadians(angle));
            int extraRows = (int) Math.ceil(Math.abs(slope) * width);
            int[] rows = new int[height + extraRows + 2];
            int offset = extraRows / 2 + 1;

            for (int y = marginY; y < height - marginY; y += sampleStep) {
                for (int x = marginX; x < width - marginX; x += sampleStep) {
                    if (isBlack(binary, x, y)) {
                        int projectedY = (int) Math.round(
                                y - slope * (x - width / 2.0)) + offset;
                        if (projectedY >= 0 && projectedY < rows.length) {
                            rows[projectedY]++;
                        }
                    }
                }
            }

            long score = 0;
            for (int count : rows) {
                score += (long) count * count;
            }
            if (score > bestScore) {
                bestScore = score;
                bestAngle = angle;
            }
        }
        return bestAngle;
    }

    BufferedImage closeSmallGaps(BufferedImage binary) {
        return erodeBlack(dilateBlack(binary));
    }

    BufferedImage dilateBlack(BufferedImage source) {
        BufferedImage result = whiteBinaryImage(source.getWidth(), source.getHeight());
        WritableRaster target = result.getRaster();
        for (int y = 1; y < source.getHeight() - 1; y++) {
            for (int x = 1; x < source.getWidth() - 1; x++) {
                if (isBlack(source, x, y)
                        || isBlack(source, x - 1, y)
                        || isBlack(source, x + 1, y)
                        || isBlack(source, x, y - 1)
                        || isBlack(source, x, y + 1)) {
                    target.setSample(x, y, 0, 0);
                }
            }
        }
        return result;
    }

    BufferedImage erodeBlack(BufferedImage source) {
        BufferedImage result = whiteBinaryImage(source.getWidth(), source.getHeight());
        WritableRaster target = result.getRaster();
        for (int y = 1; y < source.getHeight() - 1; y++) {
            for (int x = 1; x < source.getWidth() - 1; x++) {
                if (isBlack(source, x, y)
                        && isBlack(source, x - 1, y)
                        && isBlack(source, x + 1, y)
                        && isBlack(source, x, y - 1)
                        && isBlack(source, x, y + 1)) {
                    target.setSample(x, y, 0, 0);
                }
            }
        }
        return result;
    }

    BufferedImage removeBorderArtifacts(BufferedImage binary) {
        BufferedImage cleaned = copyBinaryImage(binary);
        boolean[][] visited = new boolean[cleaned.getHeight()][cleaned.getWidth()];
        Queue<Pixel> queue = new ArrayDeque<>();

        for (int x = 0; x < cleaned.getWidth(); x++) {
            enqueueBlack(cleaned, x, 0, visited, queue);
            enqueueBlack(cleaned, x, cleaned.getHeight() - 1, visited, queue);
        }
        for (int y = 0; y < cleaned.getHeight(); y++) {
            enqueueBlack(cleaned, 0, y, visited, queue);
            enqueueBlack(cleaned, cleaned.getWidth() - 1, y, visited, queue);
        }

        WritableRaster raster = cleaned.getRaster();
        int[][] directions = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        while (!queue.isEmpty()) {
            Pixel pixel = queue.remove();
            raster.setSample(pixel.x(), pixel.y(), 0, 1);
            for (int[] direction : directions) {
                enqueueBlack(
                        cleaned,
                        pixel.x() + direction[0],
                        pixel.y() + direction[1],
                        visited,
                        queue);
            }
        }
        return cleaned;
    }

    private long[][] integralImage(BufferedImage grayscale) {
        int width = grayscale.getWidth();
        int height = grayscale.getHeight();
        long[][] integral = new long[height + 1][width + 1];
        WritableRaster raster = grayscale.getRaster();
        for (int y = 1; y <= height; y++) {
            long rowSum = 0;
            for (int x = 1; x <= width; x++) {
                rowSum += raster.getSample(x - 1, y - 1, 0);
                integral[y][x] = integral[y - 1][x] + rowSum;
            }
        }
        return integral;
    }

    private long rectangleSum(
            long[][] integral, int left, int top, int right, int bottom) {
        int x1 = left;
        int y1 = top;
        int x2 = right + 1;
        int y2 = bottom + 1;
        return integral[y2][x2]
                - integral[y1][x2]
                - integral[y2][x1]
                + integral[y1][x1];
    }

    private int otsuThreshold(BufferedImage grayscale) {
        long[] histogram = new long[256];
        WritableRaster raster = grayscale.getRaster();
        for (int y = 0; y < grayscale.getHeight(); y++) {
            for (int x = 0; x < grayscale.getWidth(); x++) {
                histogram[raster.getSample(x, y, 0)]++;
            }
        }

        long pixelCount = (long) grayscale.getWidth() * grayscale.getHeight();
        long totalIntensity = 0;
        for (int intensity = 0; intensity < histogram.length; intensity++) {
            totalIntensity += (long) intensity * histogram[intensity];
        }

        long backgroundCount = 0;
        long backgroundIntensity = 0;
        double greatestVariance = -1;
        int threshold = 127;
        for (int intensity = 0; intensity < histogram.length; intensity++) {
            backgroundCount += histogram[intensity];
            if (backgroundCount == 0) {
                continue;
            }
            long foregroundCount = pixelCount - backgroundCount;
            if (foregroundCount == 0) {
                break;
            }
            backgroundIntensity += (long) intensity * histogram[intensity];
            double backgroundMean = (double) backgroundIntensity / backgroundCount;
            double foregroundMean = (double) (totalIntensity - backgroundIntensity) / foregroundCount;
            double variance = (double) backgroundCount * foregroundCount
                    * Math.pow(backgroundMean - foregroundMean, 2);
            if (variance > greatestVariance) {
                greatestVariance = variance;
                threshold = intensity;
            }
        }
        return threshold;
    }

    private void enqueueBlack(
            BufferedImage image,
            int x,
            int y,
            boolean[][] visited,
            Queue<Pixel> queue) {
        if (x < 0 || y < 0 || x >= image.getWidth() || y >= image.getHeight()
                || visited[y][x] || !isBlack(image, x, y)) {
            return;
        }
        visited[y][x] = true;
        queue.add(new Pixel(x, y));
    }

    private boolean isBlack(BufferedImage image, int x, int y) {
        return image.getRaster().getSample(x, y, 0) == 0;
    }

    private BufferedImage copyGrayImage(BufferedImage source) {
        BufferedImage copy = new BufferedImage(
                source.getWidth(), source.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        copy.setData(source.getData());
        return copy;
    }

    private BufferedImage copyBinaryImage(BufferedImage source) {
        BufferedImage copy = whiteBinaryImage(source.getWidth(), source.getHeight());
        copy.setData(source.getData());
        return copy;
    }

    private BufferedImage whiteBinaryImage(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_BINARY);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, width, height);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private record Pixel(int x, int y) {}
}
