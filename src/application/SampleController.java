package application;

import about.About;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;

public class SampleController {

    @FXML
    private Label Label_TestBoardTool;

    @FXML
    private ImageView ImageView_TestBoardTool;

    @FXML
    private MenuItem MenuItem_CN;

    @FXML
    private MenuItem MenuItem_TW;

    @FXML
    private MenuItem MenuItem_EN;

    @FXML
    private MenuItem MenuItem_OT1;

    @FXML
    private MenuItem MenuItem_OT2;

    @FXML
    private MenuItem MenuItem_About;

    @FXML
    private MenuItem MenuItem_Changed;
    
    

    private Stage stageAbout;
    
    @FXML
    void ImageView_TestBoardTool_MouseClicked(MouseEvent event) {

    }

    @FXML
    void MenuItem_About_MouseClicked(ActionEvent event) {
    	stageAbout = new Stage();
    	About about = new About();
    	about.start(stageAbout);
    }

    @FXML
    void MenuItem_CN_MouseClicked(ActionEvent event) {

    }

    @FXML
    void MenuItem_Changed_MouseClicked(ActionEvent event) {

    }

    @FXML
    void MenuItem_EN_MouseClicked(ActionEvent event) {

    }

    @FXML
    void MenuItem_OT1_MouseClicked(ActionEvent event) {

    }

    @FXML
    void MenuItem_OT2_MouseClicked(ActionEvent event) {

    }

    @FXML
    void MenuItem_TW_MouseClicked(ActionEvent event) {

    }

}