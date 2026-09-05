import cv2
import numpy as np

from ui_metrics.__main__ import main


def test_corrupt_file_does_not_abort_processing_of_other_files(tmp_path, capsys):
    good = tmp_path / "good.png"
    cv2.imwrite(str(good), np.full((8, 8, 3), 100, dtype=np.uint8))
    bad = tmp_path / "bad.png"
    bad.write_text("not a real png")

    exit_code = main([str(tmp_path)])

    out = capsys.readouterr().out
    assert exit_code == 0
    assert "good.png" in out
    assert "bad.png" in out
    assert "ERROR" in out


def test_empty_directory_reports_error_and_nonzero_exit(tmp_path, capsys):
    exit_code = main([str(tmp_path)])
    assert exit_code == 1


def test_missing_path_reports_error_and_nonzero_exit(tmp_path, capsys):
    exit_code = main([str(tmp_path / "does_not_exist.png")])
    assert exit_code == 1
