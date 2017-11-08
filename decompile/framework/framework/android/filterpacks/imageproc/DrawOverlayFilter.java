package android.filterpacks.imageproc;

import android.filterfw.core.Filter;
import android.filterfw.core.FilterContext;
import android.filterfw.core.Frame;
import android.filterfw.core.FrameFormat;
import android.filterfw.core.ShaderProgram;
import android.filterfw.format.ImageFormat;
import android.filterfw.format.ObjectFormat;
import android.filterfw.geometry.Quad;
import android.rms.iaware.AwareConstant.Database.HwAppType;

public class DrawOverlayFilter extends Filter {
    private ShaderProgram mProgram;

    public DrawOverlayFilter(String name) {
        super(name);
    }

    public void setupPorts() {
        FrameFormat imageFormatMask = ImageFormat.create(3, 3);
        addMaskedInputPort(HwAppType.SOURCE, imageFormatMask);
        addMaskedInputPort("overlay", imageFormatMask);
        addMaskedInputPort("box", ObjectFormat.fromClass(Quad.class, 1));
        addOutputBasedOnInput("image", HwAppType.SOURCE);
    }

    public FrameFormat getOutputFormat(String portName, FrameFormat inputFormat) {
        return inputFormat;
    }

    public void prepare(FilterContext context) {
        this.mProgram = ShaderProgram.createIdentity(context);
    }

    public void process(FilterContext env) {
        Frame sourceFrame = pullInput(HwAppType.SOURCE);
        Frame overlayFrame = pullInput("overlay");
        this.mProgram.setTargetRegion(((Quad) pullInput("box").getObjectValue()).translated(1.0f, 1.0f).scaled(2.0f));
        Frame output = env.getFrameManager().newFrame(sourceFrame.getFormat());
        output.setDataFromFrame(sourceFrame);
        this.mProgram.process(overlayFrame, output);
        pushOutput("image", output);
        output.release();
    }
}
