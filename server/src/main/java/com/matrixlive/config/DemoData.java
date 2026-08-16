package com.matrixlive.config;

import com.matrixlive.domain.Activity;
import com.matrixlive.domain.Participant;
import com.matrixlive.domain.Question;
import com.matrixlive.domain.Venue;
import com.matrixlive.repository.ActivityRepository;
import com.matrixlive.repository.ParticipantRepository;
import com.matrixlive.repository.QuestionRepository;
import com.matrixlive.repository.VenueRepository;
import java.time.Instant;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DemoData {
  @Bean
  CommandLineRunner seed(ActivityRepository activities, ParticipantRepository participants, QuestionRepository questions,
      VenueRepository venues) {
    return args -> {
      if (activities.count() > 0) return;
      Activity activity = activities.save(new Activity("2024 知识挑战赛", "上海", "LIVE", Instant.now()));
      venues.save(new Venue(activity.getId(), "south", "南区会场", 500));
      venues.save(new Venue(activity.getId(), "north", "北区会场", 500));
      questions.save(new Question(activity.getId(), "SINGLE", "以下哪项技术最贴近将数据转化为集体智能的核心定义？",
          "云端备份与自动归档|机器学习模型持续从数据中发现模式|通过压缩降低存储成本|以规则引擎替代人的决策", "机器学习模型持续从数据中发现模式", 100));
      questions.save(new Question(activity.getId(), "MULTIPLE", "构建可信赖的数据产品时，哪些原则应被优先考虑？",
          "数据最小化|可追溯的决策过程|默认公开所有原始数据|清晰的用户告知与授权", "数据最小化,可追溯的决策过程,清晰的用户告知与授权", 100));
      participants.save(new Participant(activity.getId(), "south", "13800002048", "林舒", "澜台科技"));
      participants.save(new Participant(activity.getId(), "south", "13800002049", "陈澈", "NOVA Lab"));
    };
  }
}
