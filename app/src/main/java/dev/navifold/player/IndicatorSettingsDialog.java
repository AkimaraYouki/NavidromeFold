package dev.navifold.player;

import android.graphics.*;
import android.text.*;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;

/** Live preference updates let an already-playing external island follow adjustments; cancel restores all values. */
final class IndicatorSettingsDialog {
 static void show(MainActivity activity){
  AppSettings s=activity.settings;
  int oldSize=s.ringCompactDp,oldX=s.islandCompactX,oldY=s.islandCompactY,oldWidth=s.islandWidthDp;
  boolean[] accepted={false};
  LinearLayout content=activity.col();content.setPadding(activity.dp(20),activity.dp(8),activity.dp(20),activity.dp(12));
  TextView help=activity.text("접힌 화면 · 세로 정방향 전용\nX: 오른쪽 + / 왼쪽 −  ·  Y: 아래 + / 위 −\n카메라 자동 위치에 더하는 값입니다. 다른 앱 위에서는 시스템 상태 표시줄에 가려질 수 있습니다.",13,activity.MUTED);content.addView(help);
  View preview=new View(activity){final Paint paint=new Paint(3);@Override protected void onDraw(Canvas c){super.onDraw(c);float d=getResources().getDisplayMetrics().density;
   QualityInfo q=AudioQuality.trackId.isEmpty()?QualityInfo.classify("audio/flac",96000,0,false,false):AudioQuality.current;
   float width=Math.min(getWidth()-8*d,s.islandWidthDp*d),diameter=s.ringCompactDp*d;
   QualityOverlay.island(c,paint,getWidth()/2f,getHeight()/2f,width,Math.max(32*d,diameter+8*d),diameter,d,getResources().getDisplayMetrics().scaledDensity,q);
   QualityOverlay.ring(c,paint,getWidth()/2f,getHeight()/2f,diameter,d,q);
  }};preview.setContentDescription("아일랜드 미리보기 · 포맷, 중앙 카메라 여백, 샘플링 레이트");preview.setLayerType(View.LAYER_TYPE_SOFTWARE,null);content.addView(preview,new LinearLayout.LayoutParams(-1,activity.dp(110)));
  content.addView(activity.text("미리보기 · 재생 중이 아니면 FLAC 96kHz 예시",11,activity.MUTED));
  Runnable changed=()->{s.save();preview.invalidate();};
  slider(activity,content,"펀치홀 링 지름",8,96,s.ringCompactDp,value->{s.ringCompactDp=value;changed.run();});
  slider(activity,content,"아일랜드 너비",140,480,s.islandWidthDp,value->{s.islandWidthDp=value;changed.run();});
  EditText x=coordinate(activity,content,"좌우 X 보정 (dp)",s.islandCompactX,value->{s.islandCompactX=value;changed.run();});
  EditText y=coordinate(activity,content,"상하 Y 보정 (dp)",s.islandCompactY,value->{s.islandCompactY=value;changed.run();});
  content.addView(activity.text("숫자 입력 범위: −3,000~+3,000dp. 화면 밖 위치는 가장자리로 제한됩니다.\n접힌 화면 · 세로 정방향에서 표시합니다. 기울이거나 평평하게 놓아도 유지됩니다. 화면이 가로·역방향으로 회전하거나 펼쳐지면 숨깁니다.",12,activity.MUTED));
  ScrollView scroll=new ScrollView(activity);scroll.setFillViewport(false);scroll.addView(content);
  AlertDialog dialog=activity.dialog().setTitle("펀치홀 · 아일랜드 조정").setView(scroll).setPositiveButton("적용",null).setNegativeButton("취소",null).setNeutralButton("위치 초기화",null).create();
  dialog.setOnDismissListener(d->{if(!accepted[0]){s.ringCompactDp=oldSize;s.islandCompactX=oldX;s.islandCompactY=oldY;s.islandWidthDp=oldWidth;s.save();}activity.remember();activity.build();});
  dialog.setOnShowListener(d->{dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{if(!valid(x)||!valid(y))return;accepted[0]=true;s.save();dialog.dismiss();});dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->{x.setText("0");y.setText("0");});});
  dialog.show();
 }
 interface Value{void set(int value);}
 static void slider(MainActivity a,LinearLayout parent,String title,int min,int max,int initial,Value change){
  TextView label=a.text(title+" · "+initial+" dp",14,a.INK);parent.addView(label);
  SeekBar bar=new SeekBar(a);bar.setMin(min);bar.setMax(max);bar.setProgress(initial);bar.setContentDescription(title);bar.setMinimumHeight(a.dp(48));parent.addView(bar,new LinearLayout.LayoutParams(-1,a.dp(48)));
  bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar b,int value,boolean user){label.setText(title+" · "+value+" dp");change.set(value);}public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}});
 }
 static EditText coordinate(MainActivity a,LinearLayout parent,String title,int initial,Value change){
  parent.addView(a.text(title,14,a.INK));LinearLayout row=new LinearLayout(a);row.setGravity(Gravity.CENTER_VERTICAL);parent.addView(row);
  EditText input=new EditText(a);input.setSingleLine(true);input.setTextColor(a.INK);input.setTextSize(16);input.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_SIGNED);input.setContentDescription(title);input.setText(String.valueOf(initial));
  row.addView(input,new LinearLayout.LayoutParams(0,a.dp(52),1));
  for(int step:new int[]{-10,-1,1,10}){Button button=a.button(step>0?"+"+step:String.valueOf(step),()->{int value=parse(input);input.setText(String.valueOf(IndicatorPolicy.clamp(value+step,-3000,3000)));});button.setPadding(0,0,0,0);row.addView(button,new LinearLayout.LayoutParams(a.dp(44),a.dp(48)));}
  input.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence t,int start,int count,int after){}public void onTextChanged(CharSequence t,int start,int before,int count){if(valid(input))change.set(parse(input));}public void afterTextChanged(Editable text){}});
  return input;
 }
 static int parse(EditText input){try{return Integer.parseInt(input.getText().toString());}catch(NumberFormatException ignored){return 0;}}
 static boolean valid(EditText input){try{int n=Integer.parseInt(input.getText().toString());if(n>=-3000&&n<=3000){input.setError(null);return true;}}catch(NumberFormatException ignored){}input.setError("−3000~3000 정수 입력");return false;}
}
