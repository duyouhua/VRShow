package com.study.xuan.stlshow.operate;

import android.content.Context;

import com.study.xuan.stlshow.callback.OnReadListener;
import com.study.xuan.stlshow.model.STLModel;
import com.study.xuan.stlshow.util.STLUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;


/**
 * Author : xuan.
 * Date : 2017/12/14.
 * Description : STL文件解析器
 */


public class STLReader implements ISTLReader{
    private OnReadListener listener;
    public float maxX = Float.MIN_VALUE;
    public float maxY = Float.MIN_VALUE;
    public float maxZ = Float.MIN_VALUE;
    public float minX = Float.MAX_VALUE;
    public float minY = Float.MAX_VALUE;
    public float minZ = Float.MAX_VALUE;
    //优化使用的数组
    private float[] normal_array = null;
    private float[] vertex_array = null;
    private int vertext_size = 0;

    public STLModel parserBinStlInSDCard(String path) throws IOException {
        File file = new File(path);
        FileInputStream fis = new FileInputStream(file);
        return parserBinStl(fis);
    }

    public STLModel parserBinStlInAssets(Context context, String fileName)
                            throws IOException {

        InputStream is = context.getAssets().open(fileName);
        if (STLUtils.isAscii(STLUtils.toByteArray(is))) {
            return parserAsciiStl(is);
        }
        return parserBinStl(is);
    }

    public STLModel parserAsciiStlAssets(Context context, String fileName) throws IOException {
        InputStream is = context.getAssets().open(fileName);
        return parserAsciiStl(is);
    }

    /**
     * 解析二进制格式的STL文件
     */
    public STLModel parserBinStl(byte[] stlBytes) {
        STLModel model = new STLModel();
        vertext_size=getIntWithLittleEndian(stlBytes, 80);;
        vertex_array=new float[vertext_size*9];
        normal_array=new float[vertext_size*9];

        for (int i = 0; i < vertext_size; i++) {
            for(int n=0;n<3;n++){
                normal_array[i*9+n*3]=Float.intBitsToFloat(getIntWithLittleEndian(stlBytes, 84 + i * 50));
                normal_array[i*9+n*3+1]=Float.intBitsToFloat(getIntWithLittleEndian(stlBytes, 84 + i * 50 + 4));
                normal_array[i*9+n*3+2]=Float.intBitsToFloat(getIntWithLittleEndian(stlBytes, 84 + i * 50 + 8));
            }
            float x = Float.intBitsToFloat(getIntWithLittleEndian(stlBytes, 84 + i * 50 + 12));
            float y = Float.intBitsToFloat(getIntWithLittleEndian(stlBytes, 84 + i * 50 + 16));
            float z = Float.intBitsToFloat(getIntWithLittleEndian(stlBytes, 84 + i * 50 + 20));
            adjustMaxMin(x, y, z);
            vertex_array[i*9]=x;
            vertex_array[i*9+1]=y;
            vertex_array[i*9+2]=z;

            x = Float.intBitsToFloat(getIntWithLittleEndian(stlBytes, 84 + i * 50 + 24));
            y = Float.intBitsToFloat(getIntWithLittleEndian(stlBytes, 84 + i * 50 + 28));
            z = Float.intBitsToFloat(getIntWithLittleEndian(stlBytes, 84 + i * 50 + 32));
            adjustMaxMin(x, y, z);
            vertex_array[i*9+3]=x;
            vertex_array[i*9+4]=y;
            vertex_array[i*9+5]=z;

            x = Float.intBitsToFloat(getIntWithLittleEndian(stlBytes, 84 + i * 50 + 36));
            y = Float.intBitsToFloat(getIntWithLittleEndian(stlBytes, 84 + i * 50 + 40));
            z = Float.intBitsToFloat(getIntWithLittleEndian(stlBytes, 84 + i * 50 + 44));
            adjustMaxMin(x, y, z);
            vertex_array[i*9+6]=x;
            vertex_array[i*9+7]=y;
            vertex_array[i*9+8]=z;

            if (i % (vertext_size / 50) == 0) {
                listener.onLoading(i,vertext_size);
            }
        }
        //将读取的数据设置到STLModel对象中
        //=================矫正中心店坐标========================
        float center_x=(maxX+minX)/2;
        float center_y=(maxY+minY)/2;
        float center_z=(maxZ+minZ)/2;

        for(int i=0;i<vertext_size*3;i++){
            adjust_coordinate(vertex_array,i*3,center_x);
            adjust_coordinate(vertex_array,i*3+1,center_y);
            adjust_coordinate(vertex_array,i*3+2,center_z);
        }

        model.setMax(maxX, maxY, maxZ);
        model.setMin(minX, minY, minZ);
        model.setSize(vertext_size);
        model.setVerts(vertex_array);
        model.setVnorms(normal_array);
        return model;
    }

    @Override
    public STLModel parserBinStl(InputStream in) {
        STLModel model = new STLModel();
        int facetCount = 0;
        byte[] facetBytes = null;
        //前面80字节是文件头，用于存贮文件名；
        try {
            in.skip(80);
            //紧接着用 4 个字节的整数来描述模型的三角面片个数
            byte[] bytes = new byte[4];
            in.read(bytes);// 读取三角面片个数
            facetCount = STLUtils.byte4ToInt(bytes, 0);
            model.setSize(facetCount);
            if (facetCount == 0) {
                in.close();
                return model;
            }

            // 每个三角面片占用固定的50个字节
            facetBytes = new byte[50 * facetCount];
            // 将所有的三角面片读取到字节数组
            in.read(facetBytes);
            //数据读取完毕后，可以把输入流关闭
            in.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        /**
         *  每个三角面片占用固定的50个字节,50字节当中：
         *  三角片的法向量：（1个向量相当于一个点）*（3维/点）*（4字节浮点数/维）=12字节
         *  三角片的三个点坐标：（3个点）*（3维/点）*（4字节浮点数/维）=36字节
         *  最后2个字节用来描述三角面片的属性信息
         * **/
        // 保存所有顶点坐标信息,一个三角形3个顶点，一个顶点3个坐标轴
        float[] verts = new float[facetCount * 3 * 3];
        // 保存所有三角面对应的法向量位置，
        // 一个三角面对应一个法向量，一个法向量有3个点
        // 而绘制模型时，是针对需要每个顶点对应的法向量，因此存储长度需要*3
        // 又同一个三角面的三个顶点的法向量是相同的，
        // 因此后面写入法向量数据的时候，只需连续写入3个相同的法向量即可
        float[] vnorms = new float[facetCount * 3 * 3];
        //保存所有三角面的属性信息
        short[] remarks = new short[facetCount];

        int stlOffset = 0;
        try {
            for (int i = 0; i < facetCount; i++) {
                if (listener != null) {
                    listener.onLoading(i, facetCount);
                }
                for (int j = 0; j < 4; j++) {
                    float x = STLUtils.byte4ToFloat(facetBytes, stlOffset);
                    float y = STLUtils.byte4ToFloat(facetBytes, stlOffset + 4);
                    float z = STLUtils.byte4ToFloat(facetBytes, stlOffset + 8);
                    stlOffset += 12;

                    if (j == 0) {//法向量
                        vnorms[i * 9] = x;
                        vnorms[i * 9 + 1] = y;
                        vnorms[i * 9 + 2] = z;
                        vnorms[i * 9 + 3] = x;
                        vnorms[i * 9 + 4] = y;
                        vnorms[i * 9 + 5] = z;
                        vnorms[i * 9 + 6] = x;
                        vnorms[i * 9 + 7] = y;
                        vnorms[i * 9 + 8] = z;
                    } else {//三个顶点
                        verts[i * 9 + (j - 1) * 3] = x;
                        verts[i * 9 + (j - 1) * 3 + 1] = y;
                        verts[i * 9 + (j - 1) * 3 + 2] = z;

                        //记录模型中三个坐标轴方向的最大最小值
                        if (i == 0 && j == 1) {
                            model.minX = model.maxX = x;
                            model.minY = model.maxY = y;
                            model.minZ = model.maxZ = z;
                        } else {
                            model.minX = Math.min(model.minX, x);
                            model.minY = Math.min(model.minY, y);
                            model.minZ = Math.min(model.minZ, z);
                            model.maxX = Math.max(model.maxX, x);
                            model.maxY = Math.max(model.maxY, y);
                            model.maxZ = Math.max(model.maxZ, z);
                        }
                    }
                }
                short r = STLUtils.byte2ToShort(facetBytes, stlOffset);
                stlOffset = stlOffset + 2;
                remarks[i] = r;
            }
        } catch (Exception e) {
            if (listener != null) {
                listener.onFailure(e);
            } else {
                e.printStackTrace();
            }
        }
        //将读取的数据设置到Model对象中
        model.setVerts(verts);
        model.setVnorms(vnorms);
        return model;
    }

    private int getIntWithLittleEndian(byte[] bytes, int offset) {
        return (0xff & bytes[offset]) | ((0xff & bytes[offset + 1]) << 8) | ((0xff & bytes[offset + 2]) << 16) | ((0xff & bytes[offset + 3]) << 24);
    }

    private void adjustMaxMin(float x, float y, float z) {
        if (x > maxX) {
            maxX = x;
        }
        if (y > maxY) {
            maxY = y;
        }
        if (z > maxZ) {
            maxZ = z;
        }
        if (x < minX) {
            minX = x;
        }
        if (y < minY) {
            minY = y;
        }
        if (z < minZ) {
            minZ = z;
        }
    }



    /**
     * 解析ASCII格式的STL文件
     */
    @Override
    public STLModel parserAsciiStl(byte[] bytes) {
        int max = 0;
        STLModel model = new STLModel();
        String stlText = new String(bytes);
        List<Float> vertexList = new ArrayList<Float>();
        //normalList.clear();

        String[] stlLines = stlText.split("\n");
        vertext_size = (stlLines.length - 2) / 7;
        vertex_array = new float[vertext_size * 9];
        normal_array = new float[vertext_size * 9];
        //progressDialog.setMax(stlLines.length);
        max = stlLines.length;

        int normal_num = 0;
        int vertex_num = 0;
        for (int i = 0; i < stlLines.length; i++) {
            String string = stlLines[i].trim();
            if (string.startsWith("facet normal ")) {
                string = string.replaceFirst("facet normal ", "");
                String[] normalValue = string.split(" ");
                for (int n = 0; n < 3; n++) {
                    normal_array[normal_num++] = Float.parseFloat(normalValue[0]);
                    normal_array[normal_num++] = Float.parseFloat(normalValue[1]);
                    normal_array[normal_num++] = Float.parseFloat(normalValue[2]);
                }
            }
            if (string.startsWith("vertex ")) {
                string = string.replaceFirst("vertex ", "");
                String[] vertexValue = string.split(" ");
                float x = Float.parseFloat(vertexValue[0]);
                float y = Float.parseFloat(vertexValue[1]);
                float z = Float.parseFloat(vertexValue[2]);
                adjustMaxMin(x, y, z);
                vertex_array[vertex_num++] = x;
                vertex_array[vertex_num++] = y;
                vertex_array[vertex_num++] = z;
            }

            if (i % (stlLines.length / 50) == 0) {
                listener.onLoading(i, max);
            }
        }
        //将读取的数据设置到STLModel对象中
        //=================矫正中心店坐标========================
        float center_x=(maxX+minX)/2;
        float center_y=(maxY+minY)/2;
        float center_z=(maxZ+minZ)/2;

        for(int i=0;i<vertext_size*3;i++){
            adjust_coordinate(vertex_array,i*3,center_x);
            adjust_coordinate(vertex_array,i*3+1,center_y);
            adjust_coordinate(vertex_array,i*3+2,center_z);
        }
        model.setMax(maxX, maxY, maxZ);
        model.setMin(minX, minY, minZ);
        model.setSize(vertext_size);
        model.setVerts(vertex_array);
        model.setVnorms(normal_array);
        return model;
    }
    /**
     * 矫正坐标  坐标圆心移动
     * @param
     * @param postion
     */
    private void adjust_coordinate(float[] vertex_array , int postion,float adjust){
        this.vertex_array[postion]-=adjust;
    }

    @Override
    public STLModel parserAsciiStl(InputStream in) {
        return null;
    }

    @Override
    public void setCallBack(OnReadListener listener) {
        this.listener = listener;
    }

}